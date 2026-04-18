package com.healthcare.interop.common.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MappingTemplate {
    private UUID id;
    private String sourceEhrCode;
    private String targetEhrCode;
    private String resourceType;
    private String version;
    private List<FieldMapping> fieldMappings;
    private boolean aiGenerated;
    private String status;
    private String createdBy;
    private Instant createdAt;

    @Data
    @Builder
    public static class FieldMapping {
        private String sourceField;
        private String targetField;
        private String transformFunction;
        private String defaultValue;
        private boolean required;
        private double confidenceScore;
        private String valueSetUrl;
        private String notes;
    }
}
