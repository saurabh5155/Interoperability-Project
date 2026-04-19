package com.healthcare.interop.adapter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.EhrEndpointConfig;
import com.healthcare.interop.common.util.JsonUtils;
import com.healthcare.interop.common.util.TemplateRenderer;
import com.healthcare.interop.adapter.auth.AuthTokenResolver;
import com.healthcare.interop.adapter.cache.EhrEndpointCacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Core HTTP execution engine.
 * Fetches EHR endpoint config from cache/DB, resolves auth,
 * renders payload template, and executes the HTTP call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicAdapterService {

    private final WebClient.Builder webClientBuilder;
    private final EhrEndpointCacheService cacheService;
    private final AuthTokenResolver authTokenResolver;
    private final ResponseExtractor responseExtractor;

    @CircuitBreaker(name = "ehr-delivery", fallbackMethod = "deliveryFallback")
    @Retry(name = "ehr-delivery")
    public Mono<JsonNode> deliver(
            String targetEhrCode,
            String operation,
            JsonNode payload,
            Map<String, String> resolvedIds) {

        return cacheService.getEndpointConfig(targetEhrCode, operation)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No endpoint config for: " + targetEhrCode + "/" + operation)))
            .flatMap(config -> executeRequest(config, payload, resolvedIds));
    }

    private Mono<JsonNode> executeRequest(
            EhrEndpointConfig config, JsonNode payload, Map<String, String> resolvedIds) {

        return authTokenResolver.resolveAuthHeader(config)
            .flatMap(authHeader -> {
                String resolvedPath = TemplateRenderer.render(config.getPathTemplate(), resolvedIds);
                String fullUrl = config.getBaseUrl() + resolvedPath;
                log.debug("Calling EHR {}: {} {}", config.getEhrCode(), config.getHttpMethod(), fullUrl);

                WebClient.RequestBodySpec request = webClientBuilder.build()
                    .method(HttpMethod.valueOf(config.getHttpMethod()))
                    .uri(fullUrl)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Source", "interop-platform");

                config.getRequestHeaders().forEach(request::header);

                String renderedBody = config.getPayloadTemplate() != null
                    ? TemplateRenderer.render(
                        JsonUtils.toJson(config.getPayloadTemplate()), resolvedIds)
                    : JsonUtils.toJson(payload);

                return request
                    .bodyValue(renderedBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .map(response -> config.getResponsePath() != null
                        ? responseExtractor.extract(response, config.getResponsePath())
                        : response)
                    .doOnNext(r -> log.info("EHR {} responded successfully", config.getEhrCode()))
                    .onErrorResume(WebClientResponseException.Unauthorized.class, e -> {
                        // Target EHR rejected the token — purge cache so the next
                        // retry (via Resilience4j @Retry) fetches a fresh token.
                        log.warn("EHR {} returned 401; invalidating cached OAuth2 token", config.getEhrCode());
                        return authTokenResolver.invalidateCachedToken(config.getEhrCode())
                            .then(Mono.error(e));
                    })
                    .doOnError(e -> log.error("EHR {} call failed: {}", config.getEhrCode(), e.getMessage()));
            });
    }

    public Mono<JsonNode> deliveryFallback(
            String targetEhrCode, String operation, JsonNode payload,
            Map<String, String> resolvedIds, Exception e) {
        log.error("Delivery fallback triggered for {}/{}: {}", targetEhrCode, operation, e.getMessage());
        return Mono.error(new RuntimeException(
            "EHR delivery failed after retries: " + targetEhrCode + " - " + e.getMessage()));
    }
}
