package com.healthcare.interop.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AssignSubscriptionRequest {
    @NotBlank
    private String sourceEhrCode;
    private UUID sourceEhrId;
    @NotBlank
    private String planCode;
    private Instant expiresAt;
    private String createdBy;
}
