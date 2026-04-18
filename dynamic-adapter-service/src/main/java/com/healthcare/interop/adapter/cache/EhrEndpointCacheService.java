package com.healthcare.interop.adapter.cache;

import com.healthcare.interop.common.model.EhrEndpointConfig;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class EhrEndpointCacheService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final WebClient.Builder webClientBuilder;

    @Value("${services.ehr-registration.url:http://ehr-registration-service:8081}")
    private String registrationServiceUrl;

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public Mono<EhrEndpointConfig> getEndpointConfig(String ehrCode, String operation) {
        String cacheKey = "endpoint:" + ehrCode + ":" + operation;
        return redisTemplate.opsForValue().get(cacheKey)
            .map(json -> JsonUtils.fromJson(json, EhrEndpointConfig.class))
            .switchIfEmpty(fetchAndCache(ehrCode, operation, cacheKey))
            .doOnNext(c -> log.debug("Endpoint config retrieved for {}/{}", ehrCode, operation));
    }

    private Mono<EhrEndpointConfig> fetchAndCache(String ehrCode, String operation, String cacheKey) {
        return webClientBuilder.baseUrl(registrationServiceUrl).build()
            .get()
            .uri("/api/v1/ehr/{ehrCode}/endpoints/{operation}", ehrCode, operation)
            .retrieve()
            .bodyToMono(EhrEndpointConfig.class)
            .flatMap(config -> redisTemplate.opsForValue()
                .set(cacheKey, JsonUtils.toJson(config), CACHE_TTL)
                .thenReturn(config))
            .doOnNext(c -> log.info("Cached endpoint config for {}/{}", ehrCode, operation));
    }

    public Mono<Boolean> invalidate(String ehrCode, String operation) {
        String cacheKey = "endpoint:" + ehrCode + ":" + operation;
        return redisTemplate.delete(cacheKey).map(count -> count > 0);
    }
}
