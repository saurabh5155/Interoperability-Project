package com.healthcare.interop.audit.consumer;

import com.healthcare.interop.audit.entity.AuditLogEntity;
import com.healthcare.interop.audit.repository.AuditLogRepository;
import com.healthcare.interop.common.model.AuditEvent;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private static final String DLQ_TOPIC = "interop.dlq";

    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @KafkaListener(
        topics = "#{T(com.healthcare.interop.common.model.AuditEvent).TOPIC}",
        groupId = "audit-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            AuditEvent event = JsonUtils.fromJson(message, AuditEvent.class);
            AuditLogEntity entity = AuditLogEntity.builder()
                .correlationId(event.getCorrelationId())
                .sourceEhrCode(event.getSourceEhrCode())
                .targetEhrCode(event.getTargetEhrCode())
                .resourceType(event.getResourceType() != null
                    ? event.getResourceType().name() : null)
                .operation(event.getOperation())
                .status(event.getStatus())
                .errorMessage(event.getErrorMessage())
                .errorCode(event.getErrorCode())
                .durationMs(event.getDurationMs())
                .aiMappingUsed(event.isAiMappingUsed())
                .aiConfidenceScore(event.getAiConfidenceScore())
                .mappingTemplateId(event.getMappingTemplateId())
                .mappingTemplateVersion(event.getMappingTemplateVersion())
                .build();

            auditLogRepository.save(entity);
            log.debug("Audit event persisted: correlationId={} status={}",
                event.getCorrelationId(), event.getStatus());

        } catch (Exception e) {
            log.error("Failed to process audit event at partition={} offset={}: {} — routing to DLQ",
                partition, offset, e.getMessage());
            sendToDlq(message, partition, offset, e);
            // Re-throw so Kafka does not commit the offset when using manual ack mode.
            // With the default auto-commit the DLQ ensures no event is silently lost.
            throw new RuntimeException("Audit event processing failed; message sent to DLQ", e);
        }
    }

    private void sendToDlq(String originalMessage, int partition, long offset, Exception cause) {
        try {
            String dlqPayload = String.format(
                "{\"originalTopic\":\"%s\",\"partition\":%d,\"offset\":%d," +
                "\"error\":\"%s\",\"originalMessage\":%s}",
                AuditEvent.TOPIC, partition, offset,
                cause.getMessage() == null ? "unknown" : cause.getMessage().replace("\"", "'"),
                originalMessage);
            kafkaTemplate.send(DLQ_TOPIC, dlqPayload);
            log.warn("Audit event forwarded to DLQ: partition={} offset={}", partition, offset);
        } catch (Exception dlqEx) {
            log.error("CRITICAL: failed to send audit event to DLQ (partition={} offset={}): {}",
                partition, offset, dlqEx.getMessage());
        }
    }
}
