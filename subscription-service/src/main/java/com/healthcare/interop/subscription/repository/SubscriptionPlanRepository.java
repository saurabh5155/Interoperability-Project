package com.healthcare.interop.subscription.repository;

import com.healthcare.interop.subscription.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, UUID> {
    Optional<SubscriptionPlanEntity> findByPlanCode(String planCode);
}
