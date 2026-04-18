package com.healthcare.interop.routing.service;

import com.healthcare.interop.common.enums.TransformStatus;
import com.healthcare.interop.common.model.IngestRequest;
import com.healthcare.interop.common.model.RouteResult;
import com.healthcare.interop.common.model.TransformationContext;
import com.healthcare.interop.common.util.JsonUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Executes a single source→target transformation pipeline.
 * Calls downstream services: fhir-service, mapping-service, dynamic-adapter, validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineExecutor {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.mapping-service.url:http://mapping-service:8085}")
    private String mappingServiceUrl;

    @CircuitBreaker(name = "pipeline-executor", fallbackMethod = "fallback")
    public Mono<RouteResult> execute(IngestRequest request, RoutingRuleDto rule) {
        long startTime = System.currentTimeMillis();
        log.info("Executing pipeline: {} → {} for {}", rule.getSourceEhrCode(),
            rule.getTargetEhrCode(), rule.getResourceType());

        TransformationContext context = TransformationContext.builder()
            .correlationId(request.getCorrelationId())
            .sourceEhrCode(rule.getSourceEhrCode())
            .targetEhrCode(rule.getTargetEhrCode())
            .resourceType(rule.getResourceType())
            .targetOperation(rule.getTargetOperation().name())
            .rawSourcePayload(request.getPayload())
            .build();

        return webClientBuilder.baseUrl(mappingServiceUrl).build()
            .post()
            .uri("/api/v1/mapping/transform")
            .bodyValue(context)
            .retrieve()
            .bodyToMono(TransformationContext.class)
            .map(result -> RouteResult.builder()
                .targetEhrCode(rule.getTargetEhrCode())
                .targetEhrName(rule.getTargetEhrCode())
                .operation(rule.getTargetOperation().name())
                .status(TransformStatus.SUCCESS)
                .httpStatusCode(200)
                .durationMs(System.currentTimeMillis() - startTime)
                .aiMappingUsed(result.isAiMappingUsed())
                .completedAt(Instant.now())
                .build())
            .doOnNext(r -> log.info("Pipeline succeeded: {} → {} in {}ms",
                rule.getSourceEhrCode(), rule.getTargetEhrCode(), r.getDurationMs()));
    }

    public Mono<RouteResult> fallback(IngestRequest request, RoutingRuleDto rule, Exception e) {
        log.error("Circuit breaker open for target: {} - {}", rule.getTargetEhrCode(), e.getMessage());
        return Mono.just(RouteResult.builder()
            .targetEhrCode(rule.getTargetEhrCode())
            .status(TransformStatus.FAILED)
            .errorMessage("Service unavailable (circuit breaker open): " + e.getMessage())
            .errorCode("CIRCUIT_BREAKER_OPEN")
            .completedAt(Instant.now())
            .build());
    }
}
