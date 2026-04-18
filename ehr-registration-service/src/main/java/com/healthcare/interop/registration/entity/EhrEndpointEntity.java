package com.healthcare.interop.registration.entity;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.TransformMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ehr_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EhrEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ehr_id", nullable = false)
    private EhrRegistrationEntity ehr;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private EhrOperationType operationType;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "path_template", nullable = false, length = 500)
    private String pathTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", columnDefinition = "jsonb")
    private Map<String, String> requestHeaders;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_template", columnDefinition = "jsonb")
    private Object payloadTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_params", columnDefinition = "jsonb")
    private List<Map<String, Object>> pathParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prerequisites", columnDefinition = "jsonb")
    private List<Map<String, Object>> prerequisites;

    @Column(name = "response_path", length = 200)
    private String responsePath;

    @Column(name = "timeout_ms")
    @Builder.Default
    private int timeoutMs = 30000;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_mode", length = 20)
    @Builder.Default
    private TransformMode transformMode = TransformMode.SYNC;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
