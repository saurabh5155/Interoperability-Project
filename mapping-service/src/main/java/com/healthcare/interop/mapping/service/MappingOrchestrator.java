package com.healthcare.interop.mapping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.exception.MappingException;
import com.healthcare.interop.common.model.MappingTemplate;
import com.healthcare.interop.common.model.TransformationContext;
import com.healthcare.interop.common.util.JsonUtils;
import com.healthcare.interop.mapping.ai.AiMappingAssistant;
import com.healthcare.interop.mapping.ai.AiMappingResponse;
import com.healthcare.interop.mapping.engine.RulesEngine;
import com.healthcare.interop.mapping.engine.RulesValidationResult;
import com.healthcare.interop.mapping.entity.MappingTemplateEntity;
import com.healthcare.interop.mapping.repository.MappingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core transformation pipeline coordinator.
 *
 * Pipeline:
 * 1. Receive TransformationContext from routing-engine
 * 2. Call fhir-service to normalize source payload to FHIR R4
 * 3. Call context-resolver for prerequisite external IDs
 * 4. Lookup mapping template from DB (deterministic path)
 * 5. If no template: call AI assistant → validate with rules engine → persist template
 * 6. Apply mappings to produce target payload
 * 7. Call dynamic-adapter to POST to target EHR
 * 8. Return enriched context with result
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingOrchestrator {

    private final MappingTemplateRepository templateRepository;
    private final AiMappingAssistant aiMappingAssistant;
    private final RulesEngine rulesEngine;
    private final SchemaInferenceService schemaInference;
    private final WebClient.Builder webClientBuilder;

    @Value("${services.fhir-service.url:http://fhir-canonical-service:8086}")
    private String fhirServiceUrl;

    @Value("${services.context-resolver.url:http://context-resolver-service:8087}")
    private String contextResolverUrl;

    @Value("${services.dynamic-adapter.url:http://dynamic-adapter-service:8084}")
    private String dynamicAdapterUrl;

    @Value("${services.validation-service.url:http://validation-service:8088}")
    private String validationServiceUrl;

    public Mono<TransformationContext> transform(TransformationContext context) {
        log.info("[{}] Starting transformation: {} → {} / {}",
            context.getCorrelationId(), context.getSourceEhrCode(),
            context.getTargetEhrCode(), context.getResourceType());

        return normalizeFhir(context)
            .flatMap(this::resolveContext)
            .flatMap(this::applyMapping)
            .flatMap(this::deliverToTarget)
            .flatMap(this::validate)
            .doOnSuccess(c -> log.info("[{}] Transformation complete (AI used: {})",
                c.getCorrelationId(), c.isAiMappingUsed()))
            .doOnError(e -> log.error("[{}] Transformation failed: {}",
                context.getCorrelationId(), e.getMessage()));
    }

    // Step 1: Normalize source payload to FHIR R4
    private Mono<TransformationContext> normalizeFhir(TransformationContext context) {
        return webClientBuilder.baseUrl(fhirServiceUrl).build()
            .post()
            .uri("/api/v1/fhir/normalize")
            .bodyValue(Map.of(
                "sourceEhrCode", context.getSourceEhrCode(),
                "resourceType", context.getResourceType().name(),
                "payload", context.getRawSourcePayload()
            ))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(fhirBundle -> {
                context.setFhirBundle(fhirBundle);
                log.debug("[{}] FHIR normalization complete", context.getCorrelationId());
                return context;
            });
    }

    // Step 2: Resolve external IDs needed by target EHR
    private Mono<TransformationContext> resolveContext(TransformationContext context) {
        return webClientBuilder.baseUrl(contextResolverUrl).build()
            .post()
            .uri("/api/v1/context/resolve")
            .bodyValue(Map.of(
                "correlationId", context.getCorrelationId().toString(),
                "targetEhrCode", context.getTargetEhrCode(),
                "operation", context.getTargetOperation(),
                "fhirPayload", context.getFhirBundle()
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .map(resolvedIds -> {
                resolvedIds.forEach((k, v) ->
                    context.addResolvedId(k.toString(), v != null ? v.toString() : null));
                log.debug("[{}] Context resolved: {} IDs", context.getCorrelationId(), resolvedIds.size());
                return context;
            })
            .onErrorResume(e -> {
                log.warn("[{}] Context resolution failed (continuing): {}", context.getCorrelationId(), e.getMessage());
                return Mono.just(context);
            });
    }

    // Step 3: Apply mapping (deterministic template OR AI-generated)
    private Mono<TransformationContext> applyMapping(TransformationContext context) {
        return Mono.fromCallable(() -> {
            Optional<MappingTemplateEntity> templateOpt = templateRepository.findLatestActive(
                context.getSourceEhrCode(), context.getTargetEhrCode(),
                context.getResourceType().name());

            List<MappingTemplate.FieldMapping> fieldMappings;

            if (templateOpt.isPresent()) {
                log.debug("[{}] Using stored mapping template: {}",
                    context.getCorrelationId(), templateOpt.get().getId());
                fieldMappings = deserializeMappings(templateOpt.get().getFieldMappings());
                context.setMappingTemplateId(templateOpt.get().getId().toString());
                context.setMappingTemplateVersion(templateOpt.get().getVersion());
                context.setAiMappingUsed(false);
            } else {
                log.info("[{}] No template found, invoking AI mapping assistant", context.getCorrelationId());
                fieldMappings = generateAndPersistAiMapping(context);
                context.setAiMappingUsed(true);
            }

            JsonNode transformed = rulesEngine.applyMappings(context.getFhirBundle(), fieldMappings);
            context.setTransformedPayload(transformed);
            return context;
        });
    }

    // Step 4: Deliver transformed payload to target EHR via dynamic-adapter
    private Mono<TransformationContext> deliverToTarget(TransformationContext context) {
        return webClientBuilder.baseUrl(dynamicAdapterUrl).build()
            .post()
            .uri("/api/v1/adapter/deliver")
            .bodyValue(Map.of(
                "correlationId", context.getCorrelationId().toString(),
                "targetEhrCode", context.getTargetEhrCode(),
                "operation", context.getTargetOperation(),
                "payload", context.getTransformedPayload(),
                "resolvedIds", context.getResolvedIds()
            ))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(response -> {
                context.addMetadata("deliveryResponse", JsonUtils.toJson(response));
                return context;
            });
    }

    // Step 5: Validate result
    private Mono<TransformationContext> validate(TransformationContext context) {
        return webClientBuilder.baseUrl(validationServiceUrl).build()
            .post()
            .uri("/api/v1/validate/fhir")
            .bodyValue(context.getTransformedPayload())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(result -> {
                context.addMetadata("validationResult", JsonUtils.toJson(result));
                return context;
            })
            .onErrorResume(e -> {
                log.warn("[{}] Validation service error (non-blocking): {}", context.getCorrelationId(), e.getMessage());
                return Mono.just(context);
            });
    }

    private List<MappingTemplate.FieldMapping> generateAndPersistAiMapping(TransformationContext context) {
        JsonNode sourceSchema = schemaInference.inferSchema(context.getRawSourcePayload());
        JsonNode targetSchema = schemaInference.getFhirSchema(context.getResourceType().name());

        List<MappingTemplate> fewShot = templateRepository
            .findFewShotExamples(context.getSourceEhrCode(), context.getTargetEhrCode())
            .stream().limit(2)
            .map(this::toMappingTemplate)
            .collect(Collectors.toList());

        AiMappingResponse aiResponse = aiMappingAssistant.generateMapping(
            context.getSourceEhrCode(), context.getTargetEhrCode(),
            context.getResourceType().name(), sourceSchema, targetSchema, fewShot);

        RulesValidationResult rulesResult = rulesEngine.validate(aiResponse, context.getResourceType().name());

        if (!rulesResult.isValid() || rulesResult.getAcceptedMappings().isEmpty()) {
            throw new MappingException(
                "AI mapping failed rules validation: " + rulesResult.getErrors());
        }

        double avgConfidence = rulesResult.getAcceptedMappings().stream()
            .mapToDouble(MappingTemplate.FieldMapping::getConfidenceScore)
            .average().orElse(0.0);
        context.setAiConfidenceScore(avgConfidence);

        persistAiTemplate(context, rulesResult.getAcceptedMappings());
        return rulesResult.getAcceptedMappings();
    }

    private void persistAiTemplate(TransformationContext context,
                                   List<MappingTemplate.FieldMapping> mappings) {
        try {
            List<Map<String, Object>> serialized = mappings.stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("sourceField", m.getSourceField());
                    map.put("targetField", m.getTargetField());
                    map.put("transformFunction", m.getTransformFunction());
                    map.put("defaultValue", m.getDefaultValue());
                    map.put("confidenceScore", m.getConfidenceScore());
                    map.put("valueSetUrl", m.getValueSetUrl());
                    return map;
                }).collect(Collectors.toList());

            MappingTemplateEntity entity = MappingTemplateEntity.builder()
                .sourceEhrCode(context.getSourceEhrCode())
                .targetEhrCode(context.getTargetEhrCode())
                .resourceType(context.getResourceType().name())
                .version("1.0.0")
                .fieldMappings(serialized)
                .aiGenerated(true)
                .status("ACTIVE")
                .createdBy("ai-assistant")
                .build();

            MappingTemplateEntity saved = templateRepository.save(entity);
            context.setMappingTemplateId(saved.getId().toString());
            context.setMappingTemplateVersion(saved.getVersion());
            log.info("[{}] AI mapping template persisted: {}", context.getCorrelationId(), saved.getId());
        } catch (Exception e) {
            log.warn("Failed to persist AI template: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<MappingTemplate.FieldMapping> deserializeMappings(List<Map<String, Object>> raw) {
        return raw.stream().map(m -> MappingTemplate.FieldMapping.builder()
            .sourceField((String) m.get("sourceField"))
            .targetField((String) m.get("targetField"))
            .transformFunction((String) m.get("transformFunction"))
            .defaultValue((String) m.get("defaultValue"))
            .confidenceScore(m.get("confidenceScore") instanceof Number n ? n.doubleValue() : 1.0)
            .valueSetUrl((String) m.get("valueSetUrl"))
            .build()
        ).collect(Collectors.toList());
    }

    private MappingTemplate toMappingTemplate(MappingTemplateEntity e) {
        return MappingTemplate.builder()
            .id(e.getId())
            .sourceEhrCode(e.getSourceEhrCode())
            .targetEhrCode(e.getTargetEhrCode())
            .resourceType(e.getResourceType())
            .version(e.getVersion())
            .aiGenerated(e.isAiGenerated())
            .fieldMappings(deserializeMappings(e.getFieldMappings()))
            .build();
    }
}
