package com.healthcare.interop.adapter.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.enums.AuthType;
import com.healthcare.interop.common.model.EhrEndpointConfig;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Resolves auth headers for each EHR based on their registered auth type.
 * OAuth2 tokens are cached in Redis with TTL to avoid repeated token calls.
 *
 * On a 401 from the target EHR the cached token is proactively invalidated
 * so the next request fetches a fresh token rather than retrying with a
 * known-stale one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthTokenResolver {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<String> resolveAuthHeader(EhrEndpointConfig config) {
        return switch (config.getAuthType()) {
            case NONE -> Mono.just("");
            case BEARER_TOKEN -> resolveBearerToken(config);
            case API_KEY -> resolveApiKey(config);
            case OAUTH2_CLIENT_CREDENTIALS -> resolveOauth2Token(config);
            case BASIC_AUTH -> resolveBasicAuth(config);
        };
    }

    /**
     * Invalidates a cached OAuth2 token for a given EHR when the caller
     * receives a 401, forcing a fresh token fetch on the next request.
     */
    public Mono<Void> invalidateCachedToken(String ehrCode) {
        String cacheKey = buildOauth2CacheKey(ehrCode);
        return redisTemplate.delete(cacheKey)
            .doOnNext(deleted -> {
                if (deleted > 0) {
                    log.info("Invalidated cached OAuth2 token for EHR: {}", ehrCode);
                }
            })
            .then();
    }

    private Mono<String> resolveBearerToken(EhrEndpointConfig config) {
        String token = config.getRequestHeaders().getOrDefault("Authorization", "");
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;
        }
        return Mono.just(token);
    }

    private Mono<String> resolveApiKey(EhrEndpointConfig config) {
        String apiKey = config.getRequestHeaders().getOrDefault("X-API-Key", "");
        return Mono.just("ApiKey " + apiKey);
    }

    private Mono<String> resolveOauth2Token(EhrEndpointConfig config) {
        String cacheKey = buildOauth2CacheKey(config.getEhrCode());
        return redisTemplate.opsForValue().get(cacheKey)
            .switchIfEmpty(fetchOauth2Token(config, cacheKey));
    }

    private Mono<String> fetchOauth2Token(EhrEndpointConfig config, String cacheKey) {
        Map<String, String> authConfig = config.getRequestHeaders();
        String tokenUrl = authConfig.getOrDefault("tokenUrl", "");
        String clientId = authConfig.getOrDefault("clientId", "");
        String clientSecret = authConfig.getOrDefault("clientSecret", "");
        String scope = authConfig.getOrDefault("scope", "");

        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        if (!scope.isBlank()) formData.add("scope", scope);

        return webClientBuilder.build()
            .post()
            .uri(tokenUrl)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .flatMap(response -> {
                String accessToken = response.path("access_token").asText();
                if (accessToken.isBlank()) {
                    return Mono.error(new IllegalStateException(
                        "OAuth2 response missing access_token for EHR: " + config.getEhrCode()));
                }
                int expiresIn = response.path("expires_in").asInt(3600);
                // Guard: never cache with a negative or zero TTL.
                long ttlSeconds = Math.max(expiresIn - 60, 30);
                String bearerHeader = "Bearer " + accessToken;
                return redisTemplate.opsForValue()
                    .set(cacheKey, bearerHeader, Duration.ofSeconds(ttlSeconds))
                    .thenReturn(bearerHeader);
            })
            .doOnNext(t -> log.debug("OAuth2 token obtained for EHR: {}", config.getEhrCode()))
            .doOnError(e -> log.error("OAuth2 token fetch failed for {}: {}",
                config.getEhrCode(), e.getMessage()));
    }

    private Mono<String> resolveBasicAuth(EhrEndpointConfig config) {
        Map<String, String> authConfig = config.getRequestHeaders();
        String username = authConfig.getOrDefault("username", "");
        String password = authConfig.getOrDefault("password", "");
        String credentials = Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes());
        return Mono.just("Basic " + credentials);
    }

    private String buildOauth2CacheKey(String ehrCode) {
        return "oauth2:token:" + ehrCode;
    }
}
