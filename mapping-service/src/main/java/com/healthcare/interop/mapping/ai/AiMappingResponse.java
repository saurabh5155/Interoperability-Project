package com.healthcare.interop.mapping.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiMappingResponse {
    private List<AiFieldMapping> mappings = new ArrayList<>();
    private List<UnmappableField> unmappableFields = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    private String sourceEhrCode;
    private String targetEhrCode;
    private String resourceType;
    private String modelUsed;
    private long promptTokens;
    private long completionTokens;

    @Data
    public static class AiFieldMapping {
        private String sourceField;
        private String targetField;
        private String transformFunction;
        private String defaultValue;
        private double confidence;
        private String valueSetUrl;
        private String notes;
    }

    @Data
    public static class UnmappableField {
        private String sourceField;
        private String reason;
    }
}
