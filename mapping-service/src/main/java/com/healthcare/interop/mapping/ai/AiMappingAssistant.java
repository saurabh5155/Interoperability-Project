package com.healthcare.interop.mapping.ai;

import com.anthropic.client.Anthropic;
import com.anthropic.models.Message;
import com.anthropic.models.MessageCreateParams;
import com.anthropic.models.Model;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.exception.MappingException;
import com.healthcare.interop.common.model.MappingTemplate;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-assisted field mapping generator using Claude.
 *
 * CRITICAL DESIGN CONSTRAINTS:
 * 1. AI output is NEVER applied directly — it flows through RulesEngine validation
 * 2. Prompt is sanitised — no PHI enters the AI call (uses schema + structure, not values)
 * 3. Strict JSON output is enforced via system prompt and post-parse validation
 * 4. Low-confidence AI mappings are rejected by the RulesEngine (threshold: 0.85)
 * 5. Every AI call is logged to the audit trail with confidence scores
 */
@Service
@Slf4j
public class AiMappingAssistant {

    private static final double MINIMUM_ACCEPTED_CONFIDENCE = 0.85;

    private static final String SYSTEM_PROMPT = """
        You are a healthcare data interoperability expert specializing in FHIR R4 field mapping.

        Your task: Given a source EHR JSON schema structure and a target FHIR R4 schema, generate
        a precise field mapping configuration.

        STRICT RULES:
        1. Output ONLY valid JSON — no explanations, no markdown, no code blocks
        2. Never invent or guess clinical values — only map structural fields
        3. For each mapping, provide a confidence score 0.0-1.0 based on semantic certainty
        4. Explicitly list fields that cannot be mapped as "unmappableFields"
        5. Use dot-notation for nested paths (e.g., "patient.name.family")
        6. Valid transformFunctions: TO_UPPERCASE, TO_LOWERCASE, DATE_FORMAT_ISO,
           SPLIT_NAME_FIRST, SPLIT_NAME_LAST, CODE_SYSTEM_LOINC, CODE_SYSTEM_SNOMED,
           CODE_SYSTEM_ICD10, BOOLEAN_TO_STRING, STRING_TO_BOOLEAN, PHONE_FORMAT, NULL

        Output schema (return ONLY this JSON, nothing else):
        {
          "mappings": [
            {
              "sourceField": "string (dot-notation path)",
              "targetField": "string (FHIR dot-notation path)",
              "transformFunction": "string|null",
              "defaultValue": "string|null",
              "confidence": 0.0,
              "valueSetUrl": "string|null",
              "notes": "string"
            }
          ],
          "unmappableFields": [
            {
              "sourceField": "string",
              "reason": "string"
            }
          ],
          "warnings": ["string"]
        }
        """;

    private final Anthropic anthropicClient;

    @Value("${ai.model:claude-sonnet-4-6}")
    private String model;

    @Value("${ai.max-tokens:4096}")
    private int maxTokens;

    public AiMappingAssistant(
            @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey) {
        this.anthropicClient = Anthropic.builder()
            .apiKey(apiKey)
            .build();
    }

    /**
     * Generates field mappings from source schema to FHIR R4 target schema.
     * Input schemas are structural (no PHI values).
     */
    @Cacheable(value = "ai-mappings", key = "#sourceEhrCode + ':' + #targetEhrCode + ':' + #resourceType")
    public AiMappingResponse generateMapping(
            String sourceEhrCode,
            String targetEhrCode,
            String resourceType,
            JsonNode sourceSchema,
            JsonNode targetSchema,
            List<MappingTemplate> fewShotExamples) {

        log.info("AI mapping requested: {} → {} for {}", sourceEhrCode, targetEhrCode, resourceType);

        String userPrompt = buildUserPrompt(
            sourceEhrCode, targetEhrCode, resourceType, sourceSchema, targetSchema, fewShotExamples);

        try {
            Message message = anthropicClient.messages().create(
                MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(maxTokens)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userPrompt)
                    .build()
            );

            String rawResponse = message.content().get(0).text().get().text();
            log.debug("AI raw response for {} → {}: {}", sourceEhrCode, targetEhrCode, rawResponse);

            AiMappingResponse response = parseAndValidateResponse(rawResponse);
            response.setSourceEhrCode(sourceEhrCode);
            response.setTargetEhrCode(targetEhrCode);
            response.setResourceType(resourceType);
            response.setModelUsed(model);
            response.setPromptTokens(message.usage().inputTokens());
            response.setCompletionTokens(message.usage().outputTokens());

            long acceptedCount = response.getMappings().stream()
                .filter(m -> m.getConfidence() >= MINIMUM_ACCEPTED_CONFIDENCE).count();
            log.info("AI mapping complete: {} total, {} above threshold ({}) for {} → {}",
                response.getMappings().size(), acceptedCount,
                MINIMUM_ACCEPTED_CONFIDENCE, sourceEhrCode, targetEhrCode);

            return response;

        } catch (Exception e) {
            log.error("AI mapping failed for {} → {}: {}", sourceEhrCode, targetEhrCode, e.getMessage());
            throw new MappingException("AI mapping generation failed: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(
            String sourceEhrCode, String targetEhrCode, String resourceType,
            JsonNode sourceSchema, JsonNode targetSchema,
            List<MappingTemplate> fewShotExamples) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Map ").append(resourceType).append(" from ")
            .append(sourceEhrCode).append(" to FHIR R4 (target: ").append(targetEhrCode).append(")\n\n");

        prompt.append("SOURCE SCHEMA (").append(sourceEhrCode).append("):\n");
        prompt.append(JsonUtils.toJson(sourceSchema)).append("\n\n");

        prompt.append("TARGET FHIR R4 SCHEMA:\n");
        prompt.append(JsonUtils.toJson(targetSchema)).append("\n\n");

        if (fewShotExamples != null && !fewShotExamples.isEmpty()) {
            prompt.append("REFERENCE MAPPINGS (use as context, not copy):\n");
            fewShotExamples.stream().limit(2).forEach(example -> {
                prompt.append("Example (").append(example.getSourceEhrCode())
                    .append(" → ").append(example.getTargetEhrCode()).append("):\n");
                prompt.append(JsonUtils.toJson(example.getFieldMappings())).append("\n");
            });
            prompt.append("\n");
        }

        prompt.append("Generate the mapping JSON now:");
        return prompt.toString();
    }

    private AiMappingResponse parseAndValidateResponse(String rawResponse) {
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
        }
        if (!JsonUtils.isValidJson(cleaned)) {
            throw new MappingException("AI returned invalid JSON: " + cleaned.substring(0, Math.min(200, cleaned.length())));
        }
        try {
            return JsonUtils.fromJson(cleaned, AiMappingResponse.class);
        } catch (Exception e) {
            throw new MappingException("Failed to parse AI mapping response", e);
        }
    }

    public static double getMinimumAcceptedConfidence() {
        return MINIMUM_ACCEPTED_CONFIDENCE;
    }
}
