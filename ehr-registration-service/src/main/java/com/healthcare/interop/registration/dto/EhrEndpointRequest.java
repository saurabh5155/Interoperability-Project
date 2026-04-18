package com.healthcare.interop.registration.dto;

import com.healthcare.interop.common.enums.EhrOperationType;
import com.healthcare.interop.common.enums.TransformMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EhrEndpointRequest {
    @NotNull
    private EhrOperationType operationType;

    @NotBlank
    private String httpMethod;

    @NotBlank
    private String pathTemplate;

    private Map<String, String> requestHeaders;
    private Object payloadTemplate;
    private List<Map<String, Object>> pathParams;
    private List<Map<String, Object>> prerequisites;
    private String responsePath;

    private int timeoutMs = 30000;
    private int retryCount = 3;
    private TransformMode transformMode = TransformMode.SYNC;
}
