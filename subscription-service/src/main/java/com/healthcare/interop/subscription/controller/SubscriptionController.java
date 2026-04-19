package com.healthcare.interop.subscription.controller;

import com.healthcare.interop.common.audit.AdminAudit;
import com.healthcare.interop.common.enums.ResourceType;
import com.healthcare.interop.subscription.dto.AssignSubscriptionRequest;
import com.healthcare.interop.subscription.dto.CreatePlanRequest;
import com.healthcare.interop.subscription.dto.CreateRoutingRuleRequest;
import com.healthcare.interop.subscription.entity.EhrSubscriptionEntity;
import com.healthcare.interop.subscription.entity.RoutingRuleEntity;
import com.healthcare.interop.subscription.entity.SubscriptionPlanEntity;
import com.healthcare.interop.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @AdminAudit(action = "CREATE_PLAN", resource = "SubscriptionPlan")
    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlanEntity> createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(subscriptionService.createPlan(request));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanEntity>> listPlans() {
        return ResponseEntity.ok(subscriptionService.listPlans());
    }

    @AdminAudit(action = "ASSIGN_SUBSCRIPTION", resource = "EhrSubscription")
    @PostMapping("/subscriptions")
    public ResponseEntity<EhrSubscriptionEntity> assignSubscription(
            @Valid @RequestBody AssignSubscriptionRequest request,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(subscriptionService.assignSubscription(request));
    }

    @AdminAudit(action = "CREATE_ROUTING_RULE", resource = "RoutingRule")
    @PostMapping("/routing-rules")
    public ResponseEntity<RoutingRuleEntity> createRoutingRule(
            @Valid @RequestBody CreateRoutingRuleRequest request,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(subscriptionService.createRoutingRule(request));
    }

    @GetMapping("/routing-rules")
    public ResponseEntity<List<RoutingRuleEntity>> listRules(
            @RequestParam String sourceEhrCode) {
        return ResponseEntity.ok(subscriptionService.listRulesForSource(sourceEhrCode));
    }

    @GetMapping("/routing-rules/active")
    public ResponseEntity<List<RoutingRuleEntity>> getActiveRoutes(
            @RequestParam String sourceEhrCode,
            @RequestParam ResourceType resourceType) {
        return ResponseEntity.ok(subscriptionService.getActiveRoutes(sourceEhrCode, resourceType));
    }

    @AdminAudit(action = "TOGGLE_ROUTING_RULE", resource = "RoutingRule")
    @PutMapping("/routing-rules/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(
            @PathVariable UUID id,
            @RequestParam boolean active,
            ServerWebExchange exchange) {
        subscriptionService.toggleRoutingRule(id, active);
        return ResponseEntity.ok(Map.of("id", id, "active", active));
    }
}
