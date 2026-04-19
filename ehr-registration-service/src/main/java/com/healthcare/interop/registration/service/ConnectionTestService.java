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

    public Mono<Boolean> test(EhrRegistrationEntity ehr) {
        return webClientBuilder.baseUrl(ehr.getBaseUrl()).build()
            .get()
            .uri("/health")
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(10))
            .thenReturn(true)
            .onErrorResume(e -> {
                log.warn("Connection test failed for EHR: {} - {}", ehr.getEhrCode(), e.getMessage());
                return Mono.just(false);
            })
            .doOnNext(ok -> {
                if (ok) log.info("Connection test passed for EHR: {}", ehr.getEhrCode());
            });
    }
}
