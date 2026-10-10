package com.aresstack.enterpriseai.application.resource.policy;

/**
 * Port for evaluating mediated resource access decisions.
 *
 * <p>This is the Tamias access gate: every operation requested by a caller
 * (user, bot, service) passes through this policy before the bronze archive
 * fulfils or denies the request.
 *
 * <p>Implementations may combine whitelist/blacklist rules, actor-type
 * restrictions, source-specific policies, and content-based decisions.
 */
public interface ResourceAccessPolicy {

    /**
     * Evaluates whether the given access request should be allowed.
     *
     * @param request the resource access request
     * @return the access decision
     */
    ResourceAccessDecision evaluate(ResourceAccessRequest request);
}
