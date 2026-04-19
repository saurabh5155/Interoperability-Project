package com.healthcare.interop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates EHR API keys on ingest routes.
 *
 * Keys are stored in Redis as "apikey:{sha256(key)}" → "{ehrCode}".
 * The ehr-registration-service populates this cache on registration and key rotation.
 */
@Slf4j
@Component
public class ApiKeyAuthFilter extends AbstractGatewayFilterFactory<ApiKeyAuthFilter.Config> {

    private static final String REDIS_KEY_PREFIX = "apikey:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ReactiveStringRedisTemplate redisTemplate;

    public ApiKeyAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return reject(exchange, "Missing API key");
            }

            String apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (apiKey.isBlank()) {
                return reject(exchange, "Empty API key");
            }

            String keyHash = sha256Hex(apiKey);
            String redisKey = REDIS_KEY_PREFIX + keyHash;

            return redisTemplate.opsForValue().get(redisKey)
                    .flatMap(ehrCode -> {
                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r.header("X-EHR-Code", ehrCode)
                                               .header("X-Source-Authenticated", "true"))
                                .build();
                        return chain.filter(mutated);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Invalid API key on {} — hash prefix: {}",
                                exchange.getRequest().getPath(),
                                keyHash.substring(0, Math.min(8, keyHash.length())));
                        return reject(exchange, "Invalid or unknown API key");
                    }));
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Failure", reason);
        return exchange.getResponse().setComplete();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class Config {}
}
