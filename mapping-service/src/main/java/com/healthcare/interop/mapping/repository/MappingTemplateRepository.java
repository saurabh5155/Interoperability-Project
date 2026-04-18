package com.healthcare.interop.mapping.repository;

import com.healthcare.interop.mapping.entity.MappingTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MappingTemplateRepository extends JpaRepository<MappingTemplateEntity, UUID> {

    @Query("""
        SELECT m FROM MappingTemplateEntity m
        WHERE m.sourceEhrCode = :sourceEhrCode
          AND m.targetEhrCode = :targetEhrCode
          AND m.resourceType = :resourceType
          AND m.status = 'ACTIVE'
        ORDER BY m.createdAt DESC
    """)
    Optional<MappingTemplateEntity> findLatestActive(
        String sourceEhrCode, String targetEhrCode, String resourceType);

    List<MappingTemplateEntity> findBySourceEhrCodeAndTargetEhrCode(
        String sourceEhrCode, String targetEhrCode);

    @Query("""
        SELECT m FROM MappingTemplateEntity m
        WHERE (m.sourceEhrCode = :ehr1 AND m.targetEhrCode = :ehr2)
           OR (m.sourceEhrCode = :ehr2 AND m.targetEhrCode = :ehr1)
        ORDER BY m.createdAt DESC
    """)
    List<MappingTemplateEntity> findFewShotExamples(String ehr1, String ehr2);
}
