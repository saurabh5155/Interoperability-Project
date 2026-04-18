package com.healthcare.interop.fhir.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.ValidationResult;
import com.healthcare.interop.fhir.service.FhirValidationService;
import com.healthcare.interop.fhir.service.GenericToFhirTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fhir")
@RequiredArgsConstructor
public class FhirTransformationController {

    private final GenericToFhirTransformer transformer;
    private final FhirValidationService validationService;

    @PostMapping("/normalize")
    public ResponseEntity<JsonNode> normalize(@RequestBody Map<String, Object> request) {
        String resourceType = (String) request.get("resourceType");
        JsonNode payload = (JsonNode) request.get("payload");
        JsonNode result = switch (resourceType.toUpperCase()) {
            case "PATIENT" -> transformer.toFhirPatient(payload);
            case "ENCOUNTER" -> transformer.toFhirEncounter(payload);
            default -> throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
        };
        return ResponseEntity.ok(result);
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody JsonNode fhirJson) {
        return ResponseEntity.ok(validationService.validate(fhirJson));
    }
}
