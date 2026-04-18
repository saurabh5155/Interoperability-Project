package com.healthcare.interop.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.experimental.UtilityClass;

import java.util.EnumSet;
import java.util.Optional;

@UtilityClass
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final Configuration JSONPATH_CONFIG = Configuration.builder()
        .options(EnumSet.of(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL))
        .build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode toJsonNode(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialization failed", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Deserialization failed: " + e.getMessage(), e);
        }
    }

    public static Optional<String> extractString(JsonNode node, String jsonPath) {
        return Optional.ofNullable(
            JsonPath.using(JSONPATH_CONFIG).parse(node.toString()).read(jsonPath, String.class)
        );
    }

    public static Optional<JsonNode> extractNode(JsonNode node, String pointer) {
        JsonNode result = node.at(pointer);
        return result.isMissingNode() ? Optional.empty() : Optional.of(result);
    }

    public static ObjectNode mergeNodes(JsonNode base, JsonNode override) {
        ObjectNode result = MAPPER.createObjectNode();
        result.setAll((ObjectNode) base);
        result.setAll((ObjectNode) override);
        return result;
    }

    public static boolean isValidJson(String json) {
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
