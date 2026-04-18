package com.healthcare.interop.registration.service;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.model.EhrEndpointConfig;
import com.healthcare.interop.registration.dto.EhrEndpointRequest;
import com.healthcare.interop.registration.entity.EhrEndpointEntity;
import com.healthcare.interop.registration.entity.EhrRegistrationEntity;
import com.healthcare.interop.registration.repository.EhrEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EhrEndpointService {

    private final EhrEndpointRepository endpointRepository;
    private final EhrRegistrationService registrationService;

    @Transactional
    @CacheEvict(value = "ehr-endpoints", key = "#ehrCode + ':' + #request.operationType")
    public EhrEndpointEntity addEndpoint(String ehrCode, EhrEndpointRequest request) {
        EhrRegistrationEntity ehr = registrationService.findByCode(ehrCode);
        EhrEndpointEntity entity = EhrEndpointEntity.builder()
            .ehr(ehr)
            .operationType(request.getOperationType())
            .httpMethod(request.getHttpMethod().toUpperCase())
            .pathTemplate(request.getPathTemplate())
            .requestHeaders(request.getRequestHeaders())
            .payloadTemplate(request.getPayloadTemplate())
            .pathParams(request.getPathParams())
            .prerequisites(request.getPrerequisites())
            .responsePath(request.getResponsePath())
            .timeoutMs(request.getTimeoutMs())
            .retryCount(request.getRetryCount())
            .transformMode(request.getTransformMode())
            .build();
        EhrEndpointEntity saved = endpointRepository.save(entity);
        log.info("Added endpoint {} for EHR: {}", request.getOperationType(), ehrCode);
        return saved;
    }

    @Cacheable(value = "ehr-endpoints", key = "#ehrCode + ':' + #operationType")
    public Optional<EhrEndpointConfig> findEndpoint(String ehrCode, EhrOperationType operationType) {
        EhrRegistrationEntity ehr = registrationService.findByCode(ehrCode);
        return endpointRepository
            .findByEhrIdAndOperationTypeAndActiveTrue(ehr.getId(), operationType)
            .map(e -> toConfig(e, ehr));
    }

    public List<EhrEndpointEntity> listEndpoints(String ehrCode) {
        EhrRegistrationEntity ehr = registrationService.findByCode(ehrCode);
        return endpointRepository.findByEhrIdAndActiveTrue(ehr.getId());
    }

    @SuppressWarnings("unchecked")
    private EhrEndpointConfig toConfig(EhrEndpointEntity e, EhrRegistrationEntity ehr) {
        return EhrEndpointConfig.builder()
            .id(e.getId())
            .ehrId(ehr.getId())
            .ehrCode(ehr.getEhrCode())
            .baseUrl(ehr.getBaseUrl())
            .operationType(e.getOperationType())
            .httpMethod(e.getHttpMethod())
            .pathTemplate(e.getPathTemplate())
            .requestHeaders(e.getRequestHeaders())
            .responsePath(e.getResponsePath())
            .timeoutMs(e.getTimeoutMs())
            .retryCount(e.getRetryCount())
            .transformMode(e.getTransformMode())
            .active(e.isActive())
            .build();
    }
}
