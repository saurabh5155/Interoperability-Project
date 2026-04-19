package com.healthcare.interop.registration.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminJwtService jwtService;

    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password-hash}")
    private String adminPasswordHash;

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!adminUsername.equals(request.username())) {
            log.warn("Admin login failed: unknown username '{}'", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        BCrypt.Result result = BCrypt.verifyer().verify(
                request.password().toCharArray(), adminPasswordHash);

        if (!result.verified) {
            log.warn("Admin login failed: wrong password for '{}'", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(adminUsername, "ADMIN");
        log.info("Admin login successful for '{}'", adminUsername);
        return Mono.just(new LoginResponse(token, "ADMIN", 24));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record LoginResponse(String token, String role, int expiresInHours) {}
}
