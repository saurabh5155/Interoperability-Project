package com.healthcare.interop.context.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.context.service.ExternalIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/context")
@RequiredArgsConstructor
public class ContextResolverController {

    private final ExternalIdResolver externalIdResolver;

    @PostMapping("/resolve")
    public Mono<ResponseEntity<Map<String, String>>> resolve(
            @RequestBody Map<String, Object> request) {
        String targetEhrCode = (String) request.get("targetEhrCode");
        String operation = (String) request.get("operation");
        JsonNode fhirPayload = (JsonNode) request.get("fhirPayload");

        return externalIdResolver.resolveIds(targetEhrCode, operation, fhirPayload)
            .map(ResponseEntity::ok);
    }

    @DeleteMapping("/cache/{targetEhrCode}")
    public ResponseEntity<Void> invalidateCache(@PathVariable String targetEhrCode) {
        return ResponseEntity.noContent().build();
    }
}
