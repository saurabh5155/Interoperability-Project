package com.healthcare.interop.routing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.interop.common.model.FanoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed idempotency guard for the ingest endpoint.
 *
 * On the first call for a given X-Idempotency-Key the key is locked in Redis
 * and the pipeline runs normally. On subsequent calls within the TTL the cached
 * result is returned immediately without re-executing the pipeline.
 *
 * Key format: "idempotency:{ehrCode}:{clientKey}"
 * Lock format: "idempotency:lock:{ehrCode}:{clientKey}"  (set while in-flight)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String RESULT_PREFIX = "idempotency:result:";
    private static final String LOCK_PREFIX   = "idempotency:lock:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${routing.idempotency.ttl-hours:24}")
    private int ttlHours;

    public Mono<FanoutResponse> getCachedResult(String ehrCode, String idempotencyKey) {
        String key = buildResultKey(ehrCode, idempotencyKey);
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        log.debug("Idempotency hit for key: {}", idempotencyKey);
                        return Mono.just(objectMapper.readValue(json, FanoutResponse.class));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialise idempotency cache entry: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    public Mono<Boolean> acquireLock(String ehrCode, String idempotencyKey) {
        String lockKey = buildLockKey(ehrCode, idempotencyKey);
        // setIfAbsent returns true only if the key did not exist (lock acquired)
        return redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));
    }

    public Mono<Void> storeResult(String ehrCode, String idempotencyKey, FanoutResponse response) {
        String resultKey = buildResultKey(ehrCode, idempotencyKey);
        String lockKey   = buildLockKey(ehrCode, idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(response);
            return redisTemplate.opsForValue()
                    .set(resultKey, json, Duration.ofHours(ttlHours))
                    .then(redisTemplate.delete(lockKey))
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise idempotency result: {}", e.getMessage());
            return redisTemplate.delete(lockKey).then();
        }
    }

    public Mono<Void> releaseLock(String ehrCode, String idempotencyKey) {
        return redisTemplate.delete(buildLockKey(ehrCode, idempotencyKey)).then();
    }

    private String buildResultKey(String ehrCode, String key) {
        return RESULT_PREFIX + ehrCode + ":" + key;
    }

    private String buildLockKey(String ehrCode, String key) {
        return LOCK_PREFIX + ehrCode + ":" + key;
    }
}
