package com.aresstack.enterpriseai.application.resource.policy;

/**
 * The outcome of a Tamias access evaluation for a resource operation.
 */
public final class ResourceAccessDecision {

    private final AccessDecisionType type;
    private final AccessReasonCode reasonCode;
    private final String explanation;

    public ResourceAccessDecision(AccessDecisionType type, AccessReasonCode reasonCode, String explanation) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (reasonCode == null) throw new IllegalArgumentException("reasonCode must not be null");
        this.type = type;
        this.reasonCode = reasonCode;
        this.explanation = explanation;
    }

    public AccessDecisionType type() { return type; }
    public AccessReasonCode reasonCode() { return reasonCode; }
    public String explanation() { return explanation; }

    public boolean isAllowed() {
        return type == AccessDecisionType.ALLOW || type == AccessDecisionType.ALLOW_CACHED_ONLY;
    }

    public static ResourceAccessDecision allow() {
        return new ResourceAccessDecision(AccessDecisionType.ALLOW, AccessReasonCode.ALLOWED, "Access allowed");
    }

    public static ResourceAccessDecision deny(AccessReasonCode reasonCode, String explanation) {
        return new ResourceAccessDecision(AccessDecisionType.DENY, reasonCode, explanation);
    }

    public static ResourceAccessDecision cachedOnly(String explanation) {
        return new ResourceAccessDecision(AccessDecisionType.ALLOW_CACHED_ONLY,
                AccessReasonCode.CACHE_ONLY_ALLOWED, explanation);
    }

    @Override
    public String toString() {
        return "ResourceAccessDecision{" + type + ", " + reasonCode + ", " + explanation + "}";
    }
}
