package com.healthcare.interop.validation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.ValidationResult;
import com.healthcare.interop.validation.pipeline.ValidationPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/validate")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationPipeline validationPipeline;

    @PostMapping("/fhir")
    public Mono<ResponseEntity<ValidationResult>> validateFhir(
            @RequestBody JsonNode payload,
            @RequestParam(defaultValue = "PATIENT") String resourceType,
            @RequestParam(defaultValue = "UNKNOWN") String targetEhrCode) {
        return validationPipeline.validateFhirPayload(payload, resourceType, targetEhrCode)
            .map(ResponseEntity::ok);
    }
}
