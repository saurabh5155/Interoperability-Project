package com.healthcare.interop.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_code", unique = true, nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    @Column(name = "max_targets")
    @Builder.Default
    private int maxTargets = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_resources", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> allowedResources = List.of("PATIENT");

    @Column(name = "rate_limit_rpm")
    @Builder.Default
    private int rateLimitRpm = 100;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
