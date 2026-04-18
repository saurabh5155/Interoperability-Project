package com.healthcare.interop.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.adapter.service.DynamicAdapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/adapter")
@RequiredArgsConstructor
public class DynamicAdapterController {

    private final DynamicAdapterService adapterService;

    @PostMapping("/deliver")
    public Mono<ResponseEntity<JsonNode>> deliver(@RequestBody Map<String, Object> request) {
        String targetEhrCode = (String) request.get("targetEhrCode");
        String operation = (String) request.get("operation");
        JsonNode payload = (JsonNode) request.get("payload");
        @SuppressWarnings("unchecked")
        Map<String, String> resolvedIds = (Map<String, String>) request.getOrDefault("resolvedIds", Map.of());

        return adapterService.deliver(targetEhrCode, operation, payload, resolvedIds)
            .map(ResponseEntity::ok);
    }
}
