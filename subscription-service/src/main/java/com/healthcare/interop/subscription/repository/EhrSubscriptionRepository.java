package com.healthcare.interop.subscription.repository;

import com.healthcare.interop.subscription.entity.EhrSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EhrSubscriptionRepository extends JpaRepository<EhrSubscriptionEntity, UUID> {
    Optional<EhrSubscriptionEntity> findBySourceEhrCodeAndStatus(String sourceEhrCode, String status);
    List<EhrSubscriptionEntity> findBySourceEhrCode(String sourceEhrCode);
}
