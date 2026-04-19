package com.healthcare.interop.routing.controller;

import com.healthcare.interop.common.model.FanoutResponse;
import com.healthcare.interop.common.model.IngestRequest;
import com.healthcare.interop.routing.service.IdempotencyService;
import com.healthcare.interop.routing.service.RoutingOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Slf4j
public class RoutingController {

    private final RoutingOrchestrator routingOrchestrator;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public Mono<ResponseEntity<FanoutResponse>> ingest(
            @Valid @RequestBody IngestRequest request,
            @RequestHeader("X-EHR-Code") String ehrCode,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        log.info("Ingest received from EHR: {} resource: {}",
            request.getSourceEhrCode(), request.getResourceType());

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return routingOrchestrator.route(request, ehrCode)
                    .map(ResponseEntity::ok);
        }

        // Check for a cached result first (fast path)
        return idempotencyService.getCachedResult(ehrCode, idempotencyKey)
                .map(cached -> {
                    log.info("Idempotency replay for key {} EHR {}", idempotencyKey, ehrCode);
                    return ResponseEntity.ok(cached);
                })
                .switchIfEmpty(
                    // Try to acquire the in-flight lock
                    idempotencyService.acquireLock(ehrCode, idempotencyKey)
                            .flatMap(locked -> {
                                if (!locked) {
                                    // Another instance is processing the same key
                                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Request with this idempotency key is already in flight");
                                }
                                return routingOrchestrator.route(request, ehrCode)
                                        .flatMap(response ->
                                            idempotencyService.storeResult(ehrCode, idempotencyKey, response)
                                                    .thenReturn(ResponseEntity.ok(response)))
                                        .doOnError(e -> idempotencyService.releaseLock(ehrCode, idempotencyKey)
                                                .subscribe());
                            })
                );
    }

    @GetMapping("/status/{correlationId}")
    public Mono<ResponseEntity<FanoutResponse>> getStatus(
            @PathVariable UUID correlationId) {
        return routingOrchestrator.getStatus(correlationId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
