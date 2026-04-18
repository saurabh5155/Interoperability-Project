package com.healthcare.interop.routing.service;

import com.healthcare.interop.common.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Fetches active routing rules from subscription-service via internal HTTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingRuleResolver {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.subscription-service.url:http://subscription-service:8082}")
    private String subscriptionServiceUrl;

    @SuppressWarnings("unchecked")
    public Mono<List<RoutingRuleDto>> resolveRules(String sourceEhrCode, ResourceType resourceType) {
        return webClientBuilder.baseUrl(subscriptionServiceUrl).build()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/admin/routing-rules/active")
                .queryParam("sourceEhrCode", sourceEhrCode)
                .queryParam("resourceType", resourceType.name())
                .build())
            .retrieve()
            .bodyToFlux(RoutingRuleDto.class)
            .collectList()
            .doOnNext(rules -> log.debug("Resolved {} routing rules for {} / {}",
                rules.size(), sourceEhrCode, resourceType))
            .onErrorReturn(List.of());
    }
}
