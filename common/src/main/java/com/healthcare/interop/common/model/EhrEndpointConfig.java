package com.healthcare.interop.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.TransformMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class EhrEndpointConfig {
    private UUID id;
    private UUID ehrId;
    private String ehrCode;
    private String baseUrl;
    private EhrOperationType operationType;
    private String httpMethod;
    private String pathTemplate;
    private Map<String, String> requestHeaders;
    private JsonNode payloadTemplate;
    private List<PathParam> pathParams;
    private List<Prerequisite> prerequisites;
    private String responsePath;
    private int timeoutMs;
    private int retryCount;
    private TransformMode transformMode;
    private boolean active;

    @Data
    @Builder
    public static class PathParam {
        private String name;
        private String description;
        private boolean required;
        private String contextKey;
    }

    @Data
    @Builder
    public static class Prerequisite {
        private String name;
        private String description;
        private String source;
        private String contextKey;
        private boolean required;
    }
}
