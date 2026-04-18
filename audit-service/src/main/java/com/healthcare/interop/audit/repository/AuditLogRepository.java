package com.healthcare.interop.audit.repository;

import com.healthcare.interop.audit.entity.AuditLogEntity;
import com.healthcare.interop.common.enums.TransformStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findByCorrelationId(UUID correlationId);
    List<AuditLogEntity> findBySourceEhrCode(String sourceEhrCode);
    List<AuditLogEntity> findByTargetEhrCode(String targetEhrCode);

    @Query("""
        SELECT a FROM AuditLogEntity a
        WHERE (:sourceEhrCode IS NULL OR a.sourceEhrCode = :sourceEhrCode)
          AND (:targetEhrCode IS NULL OR a.targetEhrCode = :targetEhrCode)
          AND (:status IS NULL OR a.status = :status)
          AND a.createdAt BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
    """)
    List<AuditLogEntity> search(
        String sourceEhrCode, String targetEhrCode, TransformStatus status,
        Instant from, Instant to);
}
