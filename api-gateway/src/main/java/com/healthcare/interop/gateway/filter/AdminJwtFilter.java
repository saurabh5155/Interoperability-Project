package com.healthcare.interop.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AdminJwtFilter extends AbstractGatewayFilterFactory<AdminJwtFilter.Config> {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    public AdminJwtFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return reject(exchange, "Missing or malformed Authorization header");
            }

            String token = authHeader.substring(7);

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String role = claims.get("role", String.class);
                if (!"ADMIN".equals(role)) {
                    return reject(exchange, "Insufficient privileges");
                }

                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-Admin-User", claims.getSubject()))
                        .build();
                return chain.filter(mutated);

            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Invalid admin JWT on {}: {}", exchange.getRequest().getPath(), e.getMessage());
                return reject(exchange, "Invalid or expired token");
            }
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Failure", reason);
        return exchange.getResponse().setComplete();
    }

    public static class Config {}
}
