package com.healthcare.interop.mapping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

/**
 * Infers structural JSON schema from a payload (no PHI — only field names and types).
 * Used to build the AI prompt without exposing clinical values.
 */
@Service
@Slf4j
public class SchemaInferenceService {

    public JsonNode inferSchema(JsonNode payload) {
        ObjectNode schema = JsonUtils.mapper().createObjectNode();
        buildSchema(payload, schema);
        return schema;
    }

    private void buildSchema(JsonNode node, ObjectNode schema) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (value.isObject()) {
                    ObjectNode nested = JsonUtils.mapper().createObjectNode();
                    buildSchema(value, nested);
                    schema.set(key, nested);
                } else if (value.isArray()) {
                    schema.put(key, "array[" + inferArrayType(value) + "]");
                } else {
                    schema.put(key, inferType(value));
                }
            }
        }
    }

    private String inferType(JsonNode value) {
        if (value.isTextual()) return "string";
        if (value.isBoolean()) return "boolean";
        if (value.isInt() || value.isLong()) return "integer";
        if (value.isDouble() || value.isFloat()) return "number";
        if (value.isNull()) return "null";
        return "unknown";
    }

    private String inferArrayType(JsonNode array) {
        if (!array.isEmpty() && array.get(0) != null) {
            return inferType(array.get(0));
        }
        return "unknown";
    }

    public JsonNode getFhirSchema(String resourceType) {
        return switch (resourceType.toUpperCase()) {
            case "PATIENT" -> getFhirPatientSchema();
            case "ENCOUNTER" -> getFhirEncounterSchema();
            case "OBSERVATION" -> getFhirObservationSchema();
            default -> JsonUtils.mapper().createObjectNode();
        };
    }

    private JsonNode getFhirPatientSchema() {
        return JsonUtils.toJsonNode("""
            {
              "resourceType": "string",
              "id": "string",
              "identifier": "array[object]",
              "active": "boolean",
              "name": "array[object: {use, family, given}]",
              "telecom": "array[object: {system, value, use}]",
              "gender": "string (male|female|other|unknown)",
              "birthDate": "string (YYYY-MM-DD)",
              "address": "array[object: {use, type, line, city, state, postalCode, country}]",
              "maritalStatus": "object: {coding}",
              "communication": "array[object: {language}]",
              "generalPractitioner": "array[object: {reference}]",
              "managingOrganization": "object: {reference, display}"
            }
            """);
    }

    private JsonNode getFhirEncounterSchema() {
        return JsonUtils.toJsonNode("""
            {
              "resourceType": "string",
              "id": "string",
              "status": "string (planned|arrived|in-progress|finished|cancelled)",
              "class": "object: {system, code, display}",
              "type": "array[object: {coding}]",
              "subject": "object: {reference, display}",
              "participant": "array[object: {type, individual}]",
              "period": "object: {start, end}",
              "reasonCode": "array[object: {coding}]",
              "diagnosis": "array[object: {condition, use, rank}]",
              "serviceProvider": "object: {reference, display}"
            }
            """);
    }

    private JsonNode getFhirObservationSchema() {
        return JsonUtils.toJsonNode("""
            {
              "resourceType": "string",
              "id": "string",
              "status": "string (registered|preliminary|final|amended)",
              "category": "array[object: {coding}]",
              "code": "object: {coding, text}",
              "subject": "object: {reference}",
              "effectiveDateTime": "string (ISO 8601)",
              "valueQuantity": "object: {value, unit, system, code}",
              "valueString": "string",
              "interpretation": "array[object: {coding}]",
              "referenceRange": "array[object: {low, high, text}]"
            }
            """);
    }
}
