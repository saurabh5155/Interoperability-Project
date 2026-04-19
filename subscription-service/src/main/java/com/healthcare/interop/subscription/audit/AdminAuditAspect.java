package com.healthcare.interop.subscription.audit;

import com.healthcare.interop.common.audit.AdminAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditAspect {

    private final AdminAuditLogRepository auditRepo;

    @Around("@annotation(com.healthcare.interop.common.audit.AdminAudit)")
    public Object auditAdminAction(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        AdminAudit annotation = method.getAnnotation(AdminAudit.class);

        String adminUser = extractAdminUser(pjp);
        String ipAddress = extractIpAddress(pjp);

        Object result = pjp.proceed();

        // For reactive return types, audit after the Mono completes
        if (result instanceof Mono<?> mono) {
            return mono.doOnSuccess(r -> saveAuditEntry(
                    adminUser, annotation.action(), annotation.resource(),
                    describeArgs(pjp), ipAddress));
        }

        // Synchronous: audit immediately after method completes
        saveAuditEntry(adminUser, annotation.action(), annotation.resource(),
                describeArgs(pjp), ipAddress);
        return result;
    }

    private void saveAuditEntry(String adminUser, String action, String resource,
                                String description, String ipAddress) {
        try {
            AdminAuditLogEntity entry = AdminAuditLogEntity.builder()
                    .adminUser(adminUser)
                    .action(action)
                    .resource(resource.isBlank() ? null : resource)
                    .description(description)
                    .ipAddress(ipAddress)
                    .build();
            auditRepo.save(entry);
            log.debug("Admin audit: user={} action={} resource={}", adminUser, action, resource);
        } catch (Exception e) {
            log.error("Failed to persist admin audit entry: {}", e.getMessage());
        }
    }

    private String extractAdminUser(ProceedingJoinPoint pjp) {
        return Arrays.stream(pjp.getArgs())
                .filter(a -> a instanceof ServerWebExchange)
                .map(a -> ((ServerWebExchange) a).getRequest()
                        .getHeaders().getFirst("X-Admin-User"))
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    private String extractIpAddress(ProceedingJoinPoint pjp) {
        return Arrays.stream(pjp.getArgs())
                .filter(a -> a instanceof ServerWebExchange)
                .map(a -> {
                    ServerHttpRequest req = ((ServerWebExchange) a).getRequest();
                    String xff = req.getHeaders().getFirst("X-Forwarded-For");
                    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
                    return Optional.ofNullable(req.getRemoteAddress())
                            .map(InetSocketAddress::getHostString)
                            .orElse(null);
                })
                .filter(ip -> ip != null)
                .findFirst()
                .orElse(null);
    }

    private String describeArgs(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        StringBuilder sb = new StringBuilder();
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        for (int i = 0; i < names.length; i++) {
            // Skip exchange/request objects — they're not meaningful in a log description
            if (args[i] instanceof ServerWebExchange) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(names[i]).append("=").append(args[i]);
        }
        return sb.toString();
    }
}
