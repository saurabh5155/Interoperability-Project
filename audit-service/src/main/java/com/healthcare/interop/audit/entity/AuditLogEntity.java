package com.healthcare.interop.audit.entity;

import com.healthcare.interop.common.enums.TransformStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "source_ehr_code", length = 50)
    private String sourceEhrCode;

    @Column(name = "target_ehr_code", length = 50)
    private String targetEhrCode;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "operation", length = 50)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TransformStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "ai_mapping_used")
    private boolean aiMappingUsed;

    @Column(name = "ai_confidence_score")
    private Double aiConfidenceScore;

    @Column(name = "mapping_template_id", length = 50)
    private String mappingTemplateId;

    @Column(name = "mapping_template_version", length = 20)
    private String mappingTemplateVersion;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
