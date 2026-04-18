package com.healthcare.interop.adapter.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.enums.AuthType;
import com.healthcare.interop.common.model.EhrEndpointConfig;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Resolves auth headers for each EHR based on their registered auth type.
 * OAuth2 tokens are cached in Redis with TTL to avoid repeated token calls.
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
        String cacheKey = "oauth2:token:" + config.getEhrCode();
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
                int expiresIn = response.path("expires_in").asInt(3600);
                String bearerHeader = "Bearer " + accessToken;
                return redisTemplate.opsForValue()
                    .set(cacheKey, bearerHeader, Duration.ofSeconds(expiresIn - 60))
                    .thenReturn(bearerHeader);
            })
            .doOnNext(t -> log.debug("OAuth2 token obtained for EHR: {}", config.getEhrCode()))
            .doOnError(e -> log.error("OAuth2 token fetch failed for {}: {}", config.getEhrCode(), e.getMessage()));
    }

    private Mono<String> resolveBasicAuth(EhrEndpointConfig config) {
        Map<String, String> authConfig = config.getRequestHeaders();
        String username = authConfig.getOrDefault("username", "");
        String password = authConfig.getOrDefault("password", "");
        String credentials = Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes());
        return Mono.just("Basic " + credentials);
    }
}
