package com.healthcare.interop.subscription.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID> {
    Page<AdminAuditLogEntity> findByAdminUserOrderByCreatedAtDesc(String adminUser, Pageable pageable);
    Page<AdminAuditLogEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
