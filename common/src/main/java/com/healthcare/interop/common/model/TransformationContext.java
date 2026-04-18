package com.healthcare.interop.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable context bag passed through the entire transformation pipeline.
 * Carries normalized FHIR, resolved external IDs, and pipeline metadata.
 */
@Data
@Builder
public class TransformationContext {
    private UUID correlationId;
    private String sourceEhrCode;
    private String targetEhrCode;
    private ResourceType resourceType;
    private String targetOperation;

    private JsonNode rawSourcePayload;
    private JsonNode fhirBundle;
    private JsonNode transformedPayload;

    @Builder.Default
    private Map<String, String> resolvedIds = new HashMap<>();

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    private String mappingTemplateId;
    private String mappingTemplateVersion;
    private boolean aiMappingUsed;
    private double aiConfidenceScore;

    private String sourceSchemaVersion;
    private String targetSchemaVersion;

    public void addResolvedId(String key, String value) {
        resolvedIds.put(key, value);
    }

    public String getResolvedId(String key) {
        return resolvedIds.get(key);
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
}
