package com.healthcare.interop.common.model;

import com.healthcare.interop.common.enums.TransformStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RouteResult {
    private String targetEhrCode;
    private String targetEhrName;
    private String operation;
    private TransformStatus status;
    private int httpStatusCode;
    private String errorMessage;
    private String errorCode;
    private long durationMs;
    private boolean aiMappingUsed;
    private Instant completedAt;
}
