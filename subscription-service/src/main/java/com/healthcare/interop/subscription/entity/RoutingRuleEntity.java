package com.healthcare.interop.subscription.entity;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.common.enums.TransformMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routing_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subscription_id", nullable = false)
    private EhrSubscriptionEntity subscription;

    @Column(name = "source_ehr_id", nullable = false)
    private UUID sourceEhrId;

    @Column(name = "source_ehr_code", nullable = false, length = 50)
    private String sourceEhrCode;

    @Column(name = "target_ehr_id", nullable = false)
    private UUID targetEhrId;

    @Column(name = "target_ehr_code", nullable = false, length = 50)
    private String targetEhrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_operation", nullable = false, length = 50)
    private EhrOperationType targetOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_mode", length = 20)
    @Builder.Default
    private TransformMode transformMode = TransformMode.SYNC;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "priority")
    @Builder.Default
    private int priority = 0;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
