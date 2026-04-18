package com.healthcare.interop.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.enums.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class IngestRequest {

    @Builder.Default
    private UUID correlationId = UUID.randomUUID();

    @NotBlank
    private String sourceEhrCode;

    @NotNull
    private ResourceType resourceType;

    @NotNull
    private JsonNode payload;

    private String payloadVersion;

    private Map<String, String> metadata;

    @Builder.Default
    private Instant receivedAt = Instant.now();
}
