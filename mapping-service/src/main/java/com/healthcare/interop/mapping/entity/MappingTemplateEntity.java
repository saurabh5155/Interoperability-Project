package com.healthcare.interop.mapping.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mapping_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_ehr_code", nullable = false, length = 50)
    private String sourceEhrCode;

    @Column(name = "target_ehr_code", nullable = false, length = 50)
    private String targetEhrCode;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mappings", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> fieldMappings;

    @Column(name = "ai_generated")
    @Builder.Default
    private boolean aiGenerated = false;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
