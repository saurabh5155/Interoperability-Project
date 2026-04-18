package com.healthcare.interop.registration.service;

import com.healthcare.interop.registration.entity.EhrRegistrationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionTestService {

    private final WebClient.Builder webClientBuilder;

    public boolean test(EhrRegistrationEntity ehr) {
        try {
            WebClient client = webClientBuilder.baseUrl(ehr.getBaseUrl()).build();
            client.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> Mono.empty())
                .block();
            log.info("Connection test passed for EHR: {}", ehr.getEhrCode());
            return true;
        } catch (Exception e) {
            log.warn("Connection test failed for EHR: {} - {}", ehr.getEhrCode(), e.getMessage());
            return false;
        }
    }
}
