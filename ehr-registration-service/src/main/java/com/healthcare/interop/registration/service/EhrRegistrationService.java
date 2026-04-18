package com.healthcare.interop.registration.service;

import com.healthcare.interop.common.exception.EhrNotFoundException;
import com.healthcare.interop.common.util.ApiKeyGenerator;
import com.healthcare.interop.registration.dto.EhrRegistrationRequest;
import com.healthcare.interop.registration.dto.EhrRegistrationResponse;
import com.healthcare.interop.registration.entity.EhrRegistrationEntity;
import com.healthcare.interop.registration.repository.EhrRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EhrRegistrationService {

    private final EhrRegistrationRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final ConnectionTestService connectionTestService;

    @Transactional
    public EhrRegistrationResponse register(EhrRegistrationRequest request) {
        if (repository.existsByEhrCode(request.getEhrCode())) {
            throw new IllegalArgumentException("EHR code already registered: " + request.getEhrCode());
        }

        String apiKey = ApiKeyGenerator.generate();
        String apiKeyHash = ApiKeyGenerator.hash(apiKey);

        var encryptedAuthConfig = encryptionService.encrypt(request.getAuthConfig());

        EhrRegistrationEntity entity = EhrRegistrationEntity.builder()
            .ehrCode(request.getEhrCode().toUpperCase())
            .displayName(request.getDisplayName())
            .orgName(request.getOrgName())
            .contactEmail(request.getContactEmail())
            .baseUrl(request.getBaseUrl())
            .authType(request.getAuthType())
            .authConfig(encryptedAuthConfig)
            .status("ACTIVE")
            .apiKeyHash(apiKeyHash)
            .description(request.getDescription())
            .version(request.getVersion())
            .build();

        EhrRegistrationEntity saved = repository.save(entity);
        log.info("Registered new EHR: {}", saved.getEhrCode());

        return toResponse(saved, apiKey);
    }

    @Cacheable(value = "ehr-registrations", key = "#ehrCode")
    public EhrRegistrationEntity findByCode(String ehrCode) {
        return repository.findByEhrCode(ehrCode.toUpperCase())
            .orElseThrow(() -> new EhrNotFoundException(ehrCode));
    }

    public List<EhrRegistrationEntity> findAll() {
        return repository.findAll();
    }

    @Transactional
    @CacheEvict(value = "ehr-registrations", key = "#ehrCode")
    public EhrRegistrationEntity suspend(String ehrCode) {
        EhrRegistrationEntity entity = findByCode(ehrCode);
        entity.setStatus("SUSPENDED");
        return repository.save(entity);
    }

    @Transactional
    @CacheEvict(value = "ehr-registrations", key = "#ehrCode")
    public EhrRegistrationEntity activate(String ehrCode) {
        EhrRegistrationEntity entity = findByCode(ehrCode);
        entity.setStatus("ACTIVE");
        return repository.save(entity);
    }

    @Transactional
    public String rotateApiKey(String ehrCode) {
        EhrRegistrationEntity entity = findByCode(ehrCode);
        String newApiKey = ApiKeyGenerator.generate();
        entity.setApiKeyHash(ApiKeyGenerator.hash(newApiKey));
        repository.save(entity);
        log.info("API key rotated for EHR: {}", ehrCode);
        return newApiKey;
    }

    public boolean testConnection(String ehrCode) {
        EhrRegistrationEntity entity = findByCode(ehrCode);
        return connectionTestService.test(entity);
    }

    public boolean validateApiKey(String apiKey) {
        String hash = ApiKeyGenerator.hash(apiKey);
        return repository.findByApiKeyHash(hash)
            .map(e -> "ACTIVE".equals(e.getStatus()))
            .orElse(false);
    }

    public EhrRegistrationEntity findByApiKey(String apiKey) {
        String hash = ApiKeyGenerator.hash(apiKey);
        return repository.findByApiKeyHash(hash)
            .orElseThrow(() -> new EhrNotFoundException("invalid-api-key"));
    }

    private EhrRegistrationResponse toResponse(EhrRegistrationEntity entity, String rawApiKey) {
        return EhrRegistrationResponse.builder()
            .id(entity.getId())
            .ehrCode(entity.getEhrCode())
            .displayName(entity.getDisplayName())
            .orgName(entity.getOrgName())
            .contactEmail(entity.getContactEmail())
            .baseUrl(entity.getBaseUrl())
            .authType(entity.getAuthType())
            .status(entity.getStatus())
            .apiKey(rawApiKey)
            .fhirVersion(entity.getFhirVersion())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
