package com.healthcare.interop.registration.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class AdminJwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expiry-hours:24}")
    private int expiryHours;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters for HMAC-SHA256 signing.");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("Admin JWT signing key initialised ({} chars).", jwtSecret.length());
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds((long) expiryHours * 3600);

        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid admin JWT: {}", e.getMessage());
            return false;
        }
    }
}
