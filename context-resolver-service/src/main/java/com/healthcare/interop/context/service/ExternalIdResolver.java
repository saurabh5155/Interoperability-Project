package com.healthcare.interop.context.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.util.JsonUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves prerequisite external IDs needed to call target EHR APIs.
 *
 * Example: Omnione EBPLP hierarchy requires orgId + facilityId
 * before any patient write call. These IDs are fetched from the
 * target EHR's registered org API endpoint and cached in Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalIdResolver {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    @Value("${services.ehr-registration.url:http://ehr-registration-service:8081}")
    private String registrationServiceUrl;

    @CircuitBreaker(name = "context-resolver", fallbackMethod = "resolveFallback")
    public Mono<Map<String, String>> resolveIds(
            String targetEhrCode, String operation, JsonNode fhirPayload) {

        String cacheKey = "ctx:" + targetEhrCode + ":" + operation;

        return redisTemplate.opsForValue().get(cacheKey)
            .<Map<String, String>>map(json -> JsonUtils.fromJson(json,
                new com.fasterxml.jackson.core.type.TypeReference<>() {}))
            .switchIfEmpty(fetchAndCacheIds(targetEhrCode, operation, fhirPayload, cacheKey))
            .doOnNext(ids -> log.debug("Resolved {} IDs for {}/{}", ids.size(), targetEhrCode, operation));
    }

    private Mono<Map<String, String>> fetchAndCacheIds(
            String targetEhrCode, String operation, JsonNode fhirPayload, String cacheKey) {

        return webClientBuilder.baseUrl(registrationServiceUrl).build()
            .get()
            .uri("/api/v1/ehr/{ehrCode}/endpoints/{op}/prerequisites", targetEhrCode, operation)
            .retrieve()
            .bodyToFlux(JsonNode.class)
            .flatMap(prereq -> resolvePrerequisite(targetEhrCode, prereq, fhirPayload))
            .collectMap(entry -> entry.getKey(), entry -> entry.getValue())
            .flatMap(ids -> {
                if (ids.isEmpty()) return Mono.just(ids);
                return redisTemplate.opsForValue()
                    .set(cacheKey, JsonUtils.toJson(ids), CACHE_TTL)
                    .thenReturn(ids);
            })
            .onErrorReturn(new HashMap<>());
    }

    private Mono<Map.Entry<String, String>> resolvePrerequisite(
            String targetEhrCode, JsonNode prereq, JsonNode fhirPayload) {
        String name = prereq.path("name").asText();
        String source = prereq.path("source").asText("ehr-api");

        if ("fhir".equals(source)) {
            String value = fhirPayload.path(prereq.path("fhirPath").asText("")).asText("");
            return Mono.just(Map.entry(name, value));
        }

        return webClientBuilder.build()
            .get()
            .uri(prereq.path("resolverUrl").asText())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(resp -> Map.entry(name, resp.path(prereq.path("responsePath").asText("id")).asText("")))
            .onErrorReturn(Map.entry(name, ""));
    }

    public Mono<Map<String, String>> resolveFallback(
            String targetEhrCode, String operation, JsonNode fhirPayload, Exception e) {
        log.warn("Context resolver circuit open for {}/{}, using empty context: {}",
            targetEhrCode, operation, e.getMessage());
        return Mono.just(new HashMap<>());
    }
}
