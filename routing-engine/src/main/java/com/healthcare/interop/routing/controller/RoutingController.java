package com.healthcare.interop.routing.controller;

import com.healthcare.interop.common.model.FanoutResponse;
import com.healthcare.interop.common.model.IngestRequest;
import com.healthcare.interop.routing.service.RoutingOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Slf4j
public class RoutingController {

    private final RoutingOrchestrator routingOrchestrator;

    @PostMapping
    public Mono<ResponseEntity<FanoutResponse>> ingest(
            @Valid @RequestBody IngestRequest request,
            @RequestHeader("X-API-Key") String apiKey) {
        log.info("Ingest received from EHR: {} resource: {}",
            request.getSourceEhrCode(), request.getResourceType());
        return routingOrchestrator.route(request, apiKey)
            .map(ResponseEntity::ok);
    }

    @GetMapping("/status/{correlationId}")
    public Mono<ResponseEntity<FanoutResponse>> getStatus(
            @PathVariable UUID correlationId) {
        return routingOrchestrator.getStatus(correlationId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
