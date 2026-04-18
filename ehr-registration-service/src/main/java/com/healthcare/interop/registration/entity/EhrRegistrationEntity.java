package com.healthcare.interop.registration.entity;

import com.healthcare.interop.common.enums.AuthType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ehr_registrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EhrRegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ehr_code", unique = true, nullable = false, length = 50)
    private String ehrCode;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "org_name", length = 200)
    private String orgName;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 30)
    private AuthType authType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private Map<String, String> authConfig;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "fhir_version", length = 10)
    @Builder.Default
    private String fhirVersion = "R4";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
