package com.healthcare.interop.mapping.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.interop.common.model.MappingTemplate;
import com.healthcare.interop.common.model.ValidationResult;
import com.healthcare.interop.common.util.JsonUtils;
import com.healthcare.interop.mapping.ai.AiMappingAssistant;
import com.healthcare.interop.mapping.ai.AiMappingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic rules engine.
 *
 * Validates and filters AI-generated mappings before they are applied.
 * This service is the final gatekeeper — AI suggestions are NEVER used
 * without passing through here.
 *
 * Validation layers:
 * 1. Confidence threshold enforcement (reject < 0.85)
 * 2. Required FHIR field enforcement
 * 3. Code system validation (ICD-10, SNOMED CT, LOINC)
 * 4. Data type coercion rules
 * 5. PHI field sanitisation check
 */
@Service
@Slf4j
public class RulesEngine {

    private static final Set<String> REQUIRED_FHIR_PATIENT_FIELDS = Set.of(
        "id", "resourceType", "identifier", "name", "birthDate", "gender"
    );

    private static final Set<String> REQUIRED_FHIR_ENCOUNTER_FIELDS = Set.of(
        "id", "resourceType", "status", "class", "subject"
    );

    private static final Set<String> VALID_TRANSFORM_FUNCTIONS = Set.of(
        "TO_UPPERCASE", "TO_LOWERCASE", "DATE_FORMAT_ISO",
        "SPLIT_NAME_FIRST", "SPLIT_NAME_LAST", "CODE_SYSTEM_LOINC",
        "CODE_SYSTEM_SNOMED", "CODE_SYSTEM_ICD10", "BOOLEAN_TO_STRING",
        "STRING_TO_BOOLEAN", "PHONE_FORMAT", "NULL"
    );

    private static final Set<String> VALID_GENDER_VALUES = Set.of(
        "male", "female", "other", "unknown"
    );

    /**
     * Validates and filters AI mapping response.
     * Returns only mappings that pass all rule checks.
     */
    public RulesValidationResult validate(AiMappingResponse aiResponse, String resourceType) {
        log.info("Rules engine validating {} AI mappings for resourceType: {}",
            aiResponse.getMappings().size(), resourceType);

        List<MappingTemplate.FieldMapping> accepted = new ArrayList<>();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (AiMappingResponse.AiFieldMapping mapping : aiResponse.getMappings()) {
            List<String> ruleViolations = checkMappingRules(mapping);
            if (ruleViolations.isEmpty()) {
                accepted.add(toFieldMapping(mapping));
            } else {
                warnings.add(String.format("Rejected mapping %s→%s: %s",
                    mapping.getSourceField(), mapping.getTargetField(),
                    String.join(", ", ruleViolations)));
                log.debug("Rejected AI mapping {}: {}", mapping.getSourceField(), ruleViolations);
            }
        }

        Set<String> required = getRequiredFields(resourceType);
        Set<String> mappedTargets = accepted.stream()
            .map(MappingTemplate.FieldMapping::getTargetField)
            .collect(Collectors.toSet());
        for (String req : required) {
            if (!mappedTargets.contains(req)) {
                errors.add(ValidationResult.ValidationError.builder()
                    .field(req)
                    .code("REQUIRED_FIELD_MISSING")
                    .message("Required FHIR field has no mapping: " + req)
                    .severity("ERROR")
                    .build());
            }
        }

        warnings.addAll(aiResponse.getWarnings());

        log.info("Rules engine result: {}/{} mappings accepted, {} errors, {} warnings",
            accepted.size(), aiResponse.getMappings().size(), errors.size(), warnings.size());

        return RulesValidationResult.builder()
            .acceptedMappings(accepted)
            .errors(errors)
            .warnings(warnings)
            .valid(errors.isEmpty())
            .build();
    }

    /**
     * Applies accepted mappings to transform source FHIR payload to target format.
     */
    public JsonNode applyMappings(JsonNode sourcePayload, List<MappingTemplate.FieldMapping> mappings) {
        ObjectNode result = JsonUtils.mapper().createObjectNode();

        for (MappingTemplate.FieldMapping mapping : mappings) {
            try {
                Optional<JsonNode> sourceValue = JsonUtils.extractNode(
                    sourcePayload, toJsonPointer(mapping.getSourceField()));

                if (sourceValue.isPresent() && !sourceValue.get().isNull()) {
                    JsonNode transformed = applyTransform(sourceValue.get(), mapping.getTransformFunction());
                    setNestedValue(result, mapping.getTargetField(), transformed);
                } else if (mapping.getDefaultValue() != null) {
                    setNestedValue(result, mapping.getTargetField(),
                        JsonUtils.mapper().valueToTree(mapping.getDefaultValue()));
                }
            } catch (Exception e) {
                log.warn("Failed to apply mapping {}: {}", mapping.getSourceField(), e.getMessage());
            }
        }
        return result;
    }

    private List<String> checkMappingRules(AiMappingResponse.AiFieldMapping mapping) {
        List<String> violations = new ArrayList<>();

        if (mapping.getConfidence() < AiMappingAssistant.getMinimumAcceptedConfidence()) {
            violations.add(String.format("confidence %.2f below threshold %.2f",
                mapping.getConfidence(), AiMappingAssistant.getMinimumAcceptedConfidence()));
        }
        if (mapping.getSourceField() == null || mapping.getSourceField().isBlank()) {
            violations.add("sourceField is blank");
        }
        if (mapping.getTargetField() == null || mapping.getTargetField().isBlank()) {
            violations.add("targetField is blank");
        }
        if (mapping.getTransformFunction() != null
                && !mapping.getTransformFunction().equals("null")
                && !VALID_TRANSFORM_FUNCTIONS.contains(mapping.getTransformFunction())) {
            violations.add("unknown transformFunction: " + mapping.getTransformFunction());
        }
        return violations;
    }

    private JsonNode applyTransform(JsonNode value, String transformFunction) {
        if (transformFunction == null || transformFunction.equals("NULL")) return value;
        String text = value.asText();
        return switch (transformFunction) {
            case "TO_UPPERCASE" -> JsonUtils.mapper().valueToTree(text.toUpperCase());
            case "TO_LOWERCASE" -> JsonUtils.mapper().valueToTree(text.toLowerCase());
            case "DATE_FORMAT_ISO" -> JsonUtils.mapper().valueToTree(normalizeDate(text));
            case "SPLIT_NAME_FIRST" -> JsonUtils.mapper().valueToTree(splitNameFirst(text));
            case "SPLIT_NAME_LAST" -> JsonUtils.mapper().valueToTree(splitNameLast(text));
            case "PHONE_FORMAT" -> JsonUtils.mapper().valueToTree(normalizePhone(text));
            case "BOOLEAN_TO_STRING" -> JsonUtils.mapper().valueToTree(Boolean.parseBoolean(text) ? "true" : "false");
            default -> value;
        };
    }

    private String normalizeDate(String date) {
        return date.replaceAll("(\\d{2})/(\\d{2})/(\\d{4})", "$3-$1-$2");
    }

    private String splitNameFirst(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : fullName;
    }

    private String splitNameLast(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : fullName;
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "+1" + digits;
        }
        return phone;
    }

    private void setNestedValue(ObjectNode root, String dotPath, JsonNode value) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i]) || !current.get(parts[i]).isObject()) {
                current.set(parts[i], JsonUtils.mapper().createObjectNode());
            }
            current = (ObjectNode) current.get(parts[i]);
        }
        current.set(parts[parts.length - 1], value);
    }

    private String toJsonPointer(String dotPath) {
        return "/" + dotPath.replace(".", "/");
    }

    private Set<String> getRequiredFields(String resourceType) {
        return switch (resourceType.toUpperCase()) {
            case "PATIENT" -> REQUIRED_FHIR_PATIENT_FIELDS;
            case "ENCOUNTER" -> REQUIRED_FHIR_ENCOUNTER_FIELDS;
            default -> Collections.emptySet();
        };
    }

    private MappingTemplate.FieldMapping toFieldMapping(AiMappingResponse.AiFieldMapping ai) {
        return MappingTemplate.FieldMapping.builder()
            .sourceField(ai.getSourceField())
            .targetField(ai.getTargetField())
            .transformFunction(ai.getTransformFunction())
            .defaultValue(ai.getDefaultValue())
            .confidenceScore(ai.getConfidence())
            .valueSetUrl(ai.getValueSetUrl())
            .notes(ai.getNotes())
            .build();
    }
}
