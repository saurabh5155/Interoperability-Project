package com.healthcare.interop.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    /**
     * Rate-limit key for EHR ingest routes — resolved from the EHR code
     * injected by ApiKeyAuthFilter, falling back to the client IP.
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String ehrCode = exchange.getRequest().getHeaders().getFirst("X-EHR-Code");
            if (ehrCode != null && !ehrCode.isBlank()) {
                return Mono.just("ehr:" + ehrCode);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Rate-limit key for admin routes — resolved from the JWT subject
     * injected by AdminJwtFilter, falling back to the client IP.
     */
    @Bean
    public KeyResolver adminJwtResolver() {
        return exchange -> {
            String adminUser = exchange.getRequest().getHeaders().getFirst("X-Admin-User");
            if (adminUser != null && !adminUser.isBlank()) {
                return Mono.just("admin:" + adminUser);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
