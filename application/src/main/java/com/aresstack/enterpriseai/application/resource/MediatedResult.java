package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessDecision;

/**
 * Result of a mediated resource operation through the bronze archive counter.
 *
 * @param <T> the type of the result payload (BronzeListing, BronzeContent, etc.)
 */
public final class MediatedResult<T> {

    private final T value;
    private final ResourceAccessDecision decision;
    private final String errorMessage;
    private final boolean success;

    private MediatedResult(T value, ResourceAccessDecision decision, String errorMessage, boolean success) {
        this.value = value;
        this.decision = decision;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    public static <T> MediatedResult<T> success(T value, ResourceAccessDecision decision) {
        return new MediatedResult<T>(value, decision, null, true);
    }

    public static <T> MediatedResult<T> denied(ResourceAccessDecision decision) {
        return new MediatedResult<T>(null, decision, null, false);
    }

    public static <T> MediatedResult<T> error(String message) {
        return new MediatedResult<T>(null, null, message, false);
    }

    public boolean isSuccess() { return success; }
    public boolean isDenied() { return !success && decision != null && !decision.isAllowed(); }
    public T value() { return value; }
    public ResourceAccessDecision decision() { return decision; }
    public String errorMessage() { return errorMessage; }

    @Override
    public String toString() {
        if (success) return "MediatedResult{SUCCESS, " + value + "}";
        if (decision != null) return "MediatedResult{DENIED, " + decision + "}";
        return "MediatedResult{ERROR, " + errorMessage + "}";
    }
}
