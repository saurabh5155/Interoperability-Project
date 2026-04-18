package com.healthcare.interop.common.exception;

import lombok.Getter;

@Getter
public class SubscriptionException extends RuntimeException {
    private final String errorCode;

    public SubscriptionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static SubscriptionException expired(String ehrCode) {
        return new SubscriptionException(
            "Subscription expired for EHR: " + ehrCode, "SUBSCRIPTION_EXPIRED");
    }

    public static SubscriptionException noActiveSubscription(String ehrCode) {
        return new SubscriptionException(
            "No active subscription for EHR: " + ehrCode, "NO_ACTIVE_SUBSCRIPTION");
    }

    public static SubscriptionException rateLimitExceeded(String ehrCode) {
        return new SubscriptionException(
            "Rate limit exceeded for EHR: " + ehrCode, "RATE_LIMIT_EXCEEDED");
    }
}
