package com.healthcare.interop.audit.controller;

import com.healthcare.interop.audit.entity.AuditLogEntity;
import com.healthcare.interop.audit.repository.AuditLogRepository;
import com.healthcare.interop.common.enums.TransformStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/{correlationId}")
    public ResponseEntity<List<AuditLogEntity>> getByCorrelationId(
            @PathVariable UUID correlationId) {
        List<AuditLogEntity> logs = auditLogRepository.findByCorrelationId(correlationId);
        return logs.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(logs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<AuditLogEntity>> search(
            @RequestParam(required = false) String sourceEhrCode,
            @RequestParam(required = false) String targetEhrCode,
            @RequestParam(required = false) TransformStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant resolvedFrom = from != null ? from : Instant.now().minusSeconds(86400);
        Instant resolvedTo = to != null ? to : Instant.now();
        return ResponseEntity.ok(
            auditLogRepository.search(sourceEhrCode, targetEhrCode, status, resolvedFrom, resolvedTo));
    }
}
