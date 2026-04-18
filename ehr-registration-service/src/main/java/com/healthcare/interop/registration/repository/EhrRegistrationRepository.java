package com.healthcare.interop.registration.repository;

import com.healthcare.interop.registration.entity.EhrRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EhrRegistrationRepository extends JpaRepository<EhrRegistrationEntity, UUID> {
    Optional<EhrRegistrationEntity> findByEhrCode(String ehrCode);
    Optional<EhrRegistrationEntity> findByApiKeyHash(String apiKeyHash);
    boolean existsByEhrCode(String ehrCode);

    @Query("SELECT e FROM EhrRegistrationEntity e WHERE e.status = 'ACTIVE'")
    java.util.List<EhrRegistrationEntity> findAllActive();
}
