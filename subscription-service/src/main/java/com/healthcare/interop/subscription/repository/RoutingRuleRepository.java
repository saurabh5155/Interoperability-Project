package com.healthcare.interop.subscription.repository;

import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.subscription.entity.RoutingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RoutingRuleRepository extends JpaRepository<RoutingRuleEntity, UUID> {

    @Query("""
        SELECT r FROM RoutingRuleEntity r
        WHERE r.sourceEhrCode = :sourceEhrCode
          AND r.resourceType = :resourceType
          AND r.active = true
          AND r.subscription.status = 'ACTIVE'
        ORDER BY r.priority ASC
    """)
    List<RoutingRuleEntity> findActiveRoutes(String sourceEhrCode, ResourceType resourceType);

    List<RoutingRuleEntity> findBySourceEhrCode(String sourceEhrCode);
    List<RoutingRuleEntity> findByTargetEhrCode(String targetEhrCode);
}
