package com.healthcare.interop.registration.controller;

import com.healthcare.interop.registration.dto.EhrEndpointRequest;
import com.healthcare.interop.registration.dto.EhrRegistrationRequest;
import com.healthcare.interop.registration.dto.EhrRegistrationResponse;
import com.healthcare.interop.registration.entity.EhrEndpointEntity;
import com.healthcare.interop.registration.entity.EhrRegistrationEntity;
import com.healthcare.interop.registration.service.EhrEndpointService;
import com.healthcare.interop.registration.service.EhrRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ehr")
@RequiredArgsConstructor
@Slf4j
public class EhrRegistrationController {

    private final EhrRegistrationService registrationService;
    private final EhrEndpointService endpointService;

    @PostMapping("/register")
    public ResponseEntity<EhrRegistrationResponse> register(
            @Valid @RequestBody EhrRegistrationRequest request) {
        EhrRegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{ehrCode}")
    public ResponseEntity<EhrRegistrationEntity> getEhr(@PathVariable String ehrCode) {
        return ResponseEntity.ok(registrationService.findByCode(ehrCode));
    }

    @GetMapping
    public ResponseEntity<List<EhrRegistrationEntity>> listAll() {
        return ResponseEntity.ok(registrationService.findAll());
    }

    @PostMapping("/{ehrCode}/test")
    public Mono<ResponseEntity<Map<String, Object>>> testConnection(@PathVariable String ehrCode) {
        return registrationService.testConnection(ehrCode)
            .map(success -> ResponseEntity.ok(Map.<String, Object>of(
                "ehrCode", ehrCode,
                "connectionSuccessful", success,
                "message", success ? "Connection verified" : "Connection failed"
            )));
    }

    @PostMapping("/{ehrCode}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable String ehrCode) {
        registrationService.suspend(ehrCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ehrCode}/activate")
    public ResponseEntity<Void> activate(@PathVariable String ehrCode) {
        registrationService.activate(ehrCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ehrCode}/rotate-key")
    public ResponseEntity<Map<String, String>> rotateApiKey(@PathVariable String ehrCode) {
        String newKey = registrationService.rotateApiKey(ehrCode);
        return ResponseEntity.ok(Map.of(
            "ehrCode", ehrCode,
            "apiKey", newKey,
            "message", "Store this key securely — it will not be shown again"
        ));
    }

    @PostMapping("/{ehrCode}/endpoints")
    public ResponseEntity<EhrEndpointEntity> addEndpoint(
            @PathVariable String ehrCode,
            @Valid @RequestBody EhrEndpointRequest request) {
        EhrEndpointEntity endpoint = endpointService.addEndpoint(ehrCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(endpoint);
    }

    @GetMapping("/{ehrCode}/endpoints")
    public ResponseEntity<List<EhrEndpointEntity>> listEndpoints(@PathVariable String ehrCode) {
        return ResponseEntity.ok(endpointService.listEndpoints(ehrCode));
    }
}
