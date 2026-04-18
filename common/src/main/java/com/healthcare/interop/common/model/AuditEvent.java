package com.healthcare.interop.common.model;

import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.common.enums.TransformStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AuditEvent {
    private UUID correlationId;
    private String sourceEhrCode;
    private String targetEhrCode;
    private ResourceType resourceType;
    private String operation;
    private TransformStatus status;
    private String errorMessage;
    private String errorCode;
    private long durationMs;
    private boolean aiMappingUsed;
    private double aiConfidenceScore;
    private String mappingTemplateId;
    private String mappingTemplateVersion;
    private String sourceIpAddress;
    private List<String> validationWarnings;
    private Instant timestamp;

    public static final String TOPIC = "interop.audit";
}
