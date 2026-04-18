package com.healthcare.interop.validation.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.healthcare.interop.common.model.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class JsonSchemaValidator {

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public ValidationResult validate(JsonNode payload, String resourceType) {
        try {
            JsonSchema schema = getSchema(resourceType);
            if (schema == null) {
                log.debug("No JSON schema found for {}, skipping schema validation", resourceType);
                return ValidationResult.success();
            }

            Set<ValidationMessage> messages = schema.validate(payload);
            if (messages.isEmpty()) return ValidationResult.success();

            List<ValidationResult.ValidationError> errors = messages.stream()
                .map(m -> ValidationResult.ValidationError.builder()
                    .field(m.getPath())
                    .code("SCHEMA_" + m.getType().toUpperCase().replace("-", "_"))
                    .message(m.getMessage())
                    .severity("ERROR")
                    .build())
                .toList();

            return ValidationResult.builder()
                .valid(false)
                .errors(errors)
                .warnings(new ArrayList<>())
                .validatorName("JSON-SCHEMA")
                .build();

        } catch (Exception e) {
            log.error("JSON schema validation error for {}: {}", resourceType, e.getMessage());
            return ValidationResult.success();
        }
    }

    private JsonSchema getSchema(String resourceType) {
        return schemaCache.computeIfAbsent(resourceType, rt -> {
            String path = "schemas/" + rt.toLowerCase() + "-fhir.schema.json";
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) return null;
                return factory.getSchema(resource.getInputStream());
            } catch (IOException e) {
                log.warn("Schema file not found: {}", path);
                return null;
            }
        });
    }
}
