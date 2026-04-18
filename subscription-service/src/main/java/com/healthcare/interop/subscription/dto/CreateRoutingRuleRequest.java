package com.healthcare.interop.subscription.dto;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.common.enums.TransformMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateRoutingRuleRequest {
    @NotBlank
    private String sourceEhrCode;
    private UUID sourceEhrId;

    @NotBlank
    private String targetEhrCode;
    private UUID targetEhrId;

    @NotNull
    private ResourceType resourceType;

    @NotNull
    private EhrOperationType targetOperation;

    private TransformMode transformMode = TransformMode.SYNC;
    private int priority = 0;
    private String createdBy;
}
