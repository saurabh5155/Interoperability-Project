package com.healthcare.interop.routing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.interop.common.enums.TransformStatus;
import com.healthcare.interop.common.exception.SubscriptionException;
import com.healthcare.interop.common.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core fanout orchestrator.
 * Given a source EHR ingest request, resolves active routing rules
 * and executes parallel transformation pipelines to all target EHRs.
 * Fanout results are persisted to Redis so status queries survive restarts
 * and work correctly in multi-instance deployments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingOrchestrator {

    private static final String STATUS_KEY_PREFIX = "fanout:status:";

    private final PipelineExecutor pipelineExecutor;
    private final RoutingRuleResolver ruleResolver;
    private final AuditEventPublisher auditPublisher;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${routing.status-cache.ttl-minutes:60}")
    private int statusCacheTtlMinutes;

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
            .flatMap(response -> cacheStatus(response).thenReturn(response))
            .doOnNext(auditPublisher::publishFanoutResult)
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
        String key = STATUS_KEY_PREFIX + correlationId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, FanoutResponse.class));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialise fanout status for {}: {}", correlationId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    private Mono<Boolean> cacheStatus(FanoutResponse response) {
        String key = STATUS_KEY_PREFIX + response.getCorrelationId();
        try {
            String json = objectMapper.writeValueAsString(response);
            return redisTemplate.opsForValue()
                    .set(key, json, Duration.ofMinutes(statusCacheTtlMinutes));
        } catch (JsonProcessingException e) {
            log.error("Failed to cache fanout status for {}: {}", response.getCorrelationId(), e.getMessage());
            return Mono.just(false);
        }
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
