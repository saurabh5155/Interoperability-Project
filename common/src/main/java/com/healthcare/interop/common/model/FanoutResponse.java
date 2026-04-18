package com.healthcare.interop.common.model;

import com.healthcare.interop.common.enums.TransformStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FanoutResponse {
    private UUID correlationId;
    private String sourceEhrCode;
    private String resourceType;
    private TransformStatus overallStatus;
    private int totalTargets;
    private int successCount;
    private int failedCount;
    private List<RouteResult> results;
    private long totalDurationMs;
    private Instant completedAt;

    public static TransformStatus computeOverallStatus(List<RouteResult> results) {
        if (results.isEmpty()) return TransformStatus.SKIPPED;
        long success = results.stream()
            .filter(r -> r.getStatus() == TransformStatus.SUCCESS).count();
        if (success == results.size()) return TransformStatus.SUCCESS;
        if (success == 0) return TransformStatus.FAILED;
        return TransformStatus.PARTIAL_SUCCESS;
    }
}
