package com.healthcare.interop.routing.service;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.common.enums.TransformMode;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RoutingRuleDto {
    private UUID ruleId;
    private String sourceEhrCode;
    private String targetEhrCode;
    private String targetEhrName;
    private ResourceType resourceType;
    private EhrOperationType targetOperation;
    private TransformMode transformMode;
    private int priority;
}
