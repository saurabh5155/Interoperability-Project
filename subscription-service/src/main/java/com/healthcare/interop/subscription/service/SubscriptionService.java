package com.healthcare.interop.subscription.service;

import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.common.exception.SubscriptionException;
import com.healthcare.interop.subscription.dto.AssignSubscriptionRequest;
import com.healthcare.interop.subscription.dto.CreatePlanRequest;
import com.healthcare.interop.subscription.dto.CreateRoutingRuleRequest;
import com.healthcare.interop.subscription.entity.EhrSubscriptionEntity;
import com.healthcare.interop.subscription.entity.RoutingRuleEntity;
import com.healthcare.interop.subscription.entity.SubscriptionPlanEntity;
import com.healthcare.interop.subscription.repository.EhrSubscriptionRepository;
import com.healthcare.interop.subscription.repository.RoutingRuleRepository;
import com.healthcare.interop.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final EhrSubscriptionRepository subscriptionRepository;
    private final RoutingRuleRepository routingRuleRepository;

    // ─── Plans ──────────────────────────────────────────────────────────────

    @Transactional
    public SubscriptionPlanEntity createPlan(CreatePlanRequest request) {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
            .planCode(request.getPlanCode().toUpperCase())
            .planName(request.getPlanName())
            .maxTargets(request.getMaxTargets())
            .allowedResources(request.getAllowedResources())
            .rateLimitRpm(request.getRateLimitRpm())
            .description(request.getDescription())
            .build();
        return planRepository.save(plan);
    }

    public List<SubscriptionPlanEntity> listPlans() {
        return planRepository.findAll();
    }

    // ─── Subscriptions ──────────────────────────────────────────────────────

    @Transactional
    public EhrSubscriptionEntity assignSubscription(AssignSubscriptionRequest request) {
        SubscriptionPlanEntity plan = planRepository.findByPlanCode(request.getPlanCode())
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.getPlanCode()));

        EhrSubscriptionEntity subscription = EhrSubscriptionEntity.builder()
            .sourceEhrId(request.getSourceEhrId())
            .sourceEhrCode(request.getSourceEhrCode().toUpperCase())
            .plan(plan)
            .status("ACTIVE")
            .expiresAt(request.getExpiresAt())
            .createdBy(request.getCreatedBy())
            .build();

        EhrSubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Assigned plan {} to EHR {}", plan.getPlanCode(), request.getSourceEhrCode());
        return saved;
    }

    public EhrSubscriptionEntity getActiveSubscription(String sourceEhrCode) {
        return subscriptionRepository.findBySourceEhrCodeAndStatus(sourceEhrCode, "ACTIVE")
            .filter(s -> !s.isExpired())
            .orElseThrow(() -> SubscriptionException.noActiveSubscription(sourceEhrCode));
    }

    // ─── Routing Rules ───────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "routing-rules", key = "#request.sourceEhrCode + ':' + #request.resourceType")
    public RoutingRuleEntity createRoutingRule(CreateRoutingRuleRequest request) {
        EhrSubscriptionEntity subscription = getActiveSubscription(request.getSourceEhrCode());
        int existingTargets = routingRuleRepository
            .findBySourceEhrCode(request.getSourceEhrCode()).size();
        if (existingTargets >= subscription.getPlan().getMaxTargets()) {
            throw SubscriptionException.rateLimitExceeded(request.getSourceEhrCode());
        }

        RoutingRuleEntity rule = RoutingRuleEntity.builder()
            .subscription(subscription)
            .sourceEhrId(request.getSourceEhrId())
            .sourceEhrCode(request.getSourceEhrCode().toUpperCase())
            .targetEhrId(request.getTargetEhrId())
            .targetEhrCode(request.getTargetEhrCode().toUpperCase())
            .resourceType(request.getResourceType())
            .targetOperation(request.getTargetOperation())
            .transformMode(request.getTransformMode())
            .priority(request.getPriority())
            .createdBy(request.getCreatedBy())
            .build();

        RoutingRuleEntity saved = routingRuleRepository.save(rule);
        log.info("Created routing rule: {} → {} for {}",
            request.getSourceEhrCode(), request.getTargetEhrCode(), request.getResourceType());
        return saved;
    }

    @Cacheable(value = "routing-rules", key = "#sourceEhrCode + ':' + #resourceType")
    public List<RoutingRuleEntity> getActiveRoutes(String sourceEhrCode, ResourceType resourceType) {
        return routingRuleRepository.findActiveRoutes(sourceEhrCode, resourceType);
    }

    @Transactional
    @CacheEvict(value = "routing-rules", allEntries = true)
    public void toggleRoutingRule(UUID ruleId, boolean active) {
        routingRuleRepository.findById(ruleId).ifPresent(rule -> {
            rule.setActive(active);
            routingRuleRepository.save(rule);
            log.info("Routing rule {} set to active={}", ruleId, active);
        });
    }

    public List<RoutingRuleEntity> listRulesForSource(String sourceEhrCode) {
        return routingRuleRepository.findBySourceEhrCode(sourceEhrCode);
    }
}
