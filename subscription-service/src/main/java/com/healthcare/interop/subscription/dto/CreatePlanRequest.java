package com.healthcare.interop.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class CreatePlanRequest {
    @NotBlank
    private String planCode;
    @NotBlank
    private String planName;
    @Positive
    private int maxTargets = 1;
    private List<String> allowedResources = List.of("PATIENT");
    @Positive
    private int rateLimitRpm = 100;
    private String description;
}
