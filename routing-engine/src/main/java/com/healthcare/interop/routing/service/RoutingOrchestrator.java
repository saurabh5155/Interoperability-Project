package com.healthcare.interop.routing.service;

import com.healthcare.interop.common.enums.TransformStatus;
import com.healthcare.interop.common.exception.SubscriptionException;
import com.healthcare.interop.common.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core fanout orchestrator.
 * Given a source EHR ingest request, resolves active routing rules
 * and executes parallel transformation pipelines to all target EHRs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingOrchestrator {

    private final WebClient.Builder webClientBuilder;
    private final PipelineExecutor pipelineExecutor;
    private final RoutingRuleResolver ruleResolver;
    private final AuditEventPublisher auditPublisher;

    private final Map<UUID, FanoutResponse> statusCache = new ConcurrentHashMap<>();

    public Mono<FanoutResponse> route(IngestRequest request, String apiKey) {
        long startTime = System.currentTimeMillis();

        return ruleResolver.resolveRules(request.getSourceEhrCode(), request.getResourceType())
            .switchIfEmpty(Mono.error(SubscriptionException.noActiveSubscription(
                request.getSourceEhrCode())))
            .flatMap(rules -> {
                if (rules.isEmpty()) {
                    return Mono.just(buildEmptyResponse(request));
                }
                return executeFanout(request, rules, startTime);
            })
            .doOnNext(response -> {
                statusCache.put(response.getCorrelationId(), response);
                auditPublisher.publishFanoutResult(response);
            })
            .doOnError(e -> log.error("Routing failed for {}: {}", request.getSourceEhrCode(), e.getMessage()));
    }

    private Mono<FanoutResponse> executeFanout(
            IngestRequest request, List<RoutingRuleDto> rules, long startTime) {

        log.info("Fanning out to {} targets for EHR: {}", rules.size(), request.getSourceEhrCode());

        return Flux.fromIterable(rules)
            .flatMap(rule -> pipelineExecutor.execute(request, rule)
                .onErrorResume(e -> {
                    log.error("Pipeline failed for target {}: {}", rule.getTargetEhrCode(), e.getMessage());
                    return Mono.just(RouteResult.builder()
                        .targetEhrCode(rule.getTargetEhrCode())
                        .targetEhrName(rule.getTargetEhrName())
                        .operation(rule.getTargetOperation())
                        .status(TransformStatus.FAILED)
                        .errorMessage(e.getMessage())
                        .completedAt(Instant.now())
                        .build());
                }))
            .collectList()
            .map(results -> {
                long duration = System.currentTimeMillis() - startTime;
                TransformStatus overall = FanoutResponse.computeOverallStatus(results);
                FanoutResponse response = FanoutResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .sourceEhrCode(request.getSourceEhrCode())
                    .resourceType(request.getResourceType().name())
                    .overallStatus(overall)
                    .totalTargets(results.size())
                    .successCount((int) results.stream()
                        .filter(r -> r.getStatus() == TransformStatus.SUCCESS).count())
                    .failedCount((int) results.stream()
                        .filter(r -> r.getStatus() == TransformStatus.FAILED).count())
                    .results(results)
                    .totalDurationMs(duration)
                    .completedAt(Instant.now())
                    .build();
                log.info("Fanout complete: {} - {}/{} succeeded in {}ms",
                    request.getCorrelationId(), response.getSuccessCount(),
                    response.getTotalTargets(), duration);
                return response;
            });
    }

    public Mono<FanoutResponse> getStatus(UUID correlationId) {
        return Mono.justOrEmpty(statusCache.get(correlationId));
    }

    private FanoutResponse buildEmptyResponse(IngestRequest request) {
        return FanoutResponse.builder()
            .correlationId(request.getCorrelationId())
            .sourceEhrCode(request.getSourceEhrCode())
            .resourceType(request.getResourceType().name())
            .overallStatus(TransformStatus.SKIPPED)
            .totalTargets(0)
            .successCount(0)
            .failedCount(0)
            .results(List.of())
            .completedAt(Instant.now())
            .build();
    }
}
