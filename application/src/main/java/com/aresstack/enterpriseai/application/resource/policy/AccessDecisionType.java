package com.aresstack.enterpriseai.application.resource.policy;

/**
 * The type of access decision returned by Tamias.
 */
public enum AccessDecisionType {
    /** Access is allowed. */
    ALLOW,
    /** Access is denied. */
    DENY,
    /** Access requires authentication at the source. */
    REQUIRE_AUTH,
    /** Access requires a source-level check before proceeding. */
    REQUIRE_SOURCE_CHECK,
    /** Only cached/archived content may be returned (no external fetch). */
    ALLOW_CACHED_ONLY
}
