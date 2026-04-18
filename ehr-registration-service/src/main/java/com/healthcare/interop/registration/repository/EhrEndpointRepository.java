package com.healthcare.interop.registration.repository;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.registration.entity.EhrEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EhrEndpointRepository extends JpaRepository<EhrEndpointEntity, UUID> {
    List<EhrEndpointEntity> findByEhrIdAndActiveTrue(UUID ehrId);
    Optional<EhrEndpointEntity> findByEhrIdAndOperationTypeAndActiveTrue(
        UUID ehrId, EhrOperationType operationType);
}
