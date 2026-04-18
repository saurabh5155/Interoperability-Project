package com.healthcare.interop.routing.kafka;

import com.healthcare.interop.common.model.AuditEvent;
import com.healthcare.interop.common.model.FanoutResponse;
import com.healthcare.interop.common.model.RouteResult;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishFanoutResult(FanoutResponse fanoutResponse) {
        for (RouteResult result : fanoutResponse.getResults()) {
            AuditEvent event = AuditEvent.builder()
                .correlationId(fanoutResponse.getCorrelationId())
                .sourceEhrCode(fanoutResponse.getSourceEhrCode())
                .targetEhrCode(result.getTargetEhrCode())
                .operation(result.getOperation())
                .status(result.getStatus())
                .errorMessage(result.getErrorMessage())
                .durationMs(result.getDurationMs())
                .aiMappingUsed(result.isAiMappingUsed())
                .timestamp(Instant.now())
                .build();
            try {
                kafkaTemplate.send(AuditEvent.TOPIC,
                    fanoutResponse.getCorrelationId().toString(),
                    JsonUtils.toJson(event));
            } catch (Exception e) {
                log.error("Failed to publish audit event for correlationId={}: {}",
                    fanoutResponse.getCorrelationId(), e.getMessage());
            }
        }
    }
}
