package com.healthcare.interop.registration.dto;

import com.healthcare.interop.common.enums.AuthType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EhrRegistrationResponse {
    private UUID id;
    private String ehrCode;
    private String displayName;
    private String orgName;
    private String contactEmail;
    private String baseUrl;
    private AuthType authType;
    private String status;
    private String apiKey;
    private String fhirVersion;
    private Instant createdAt;
}
