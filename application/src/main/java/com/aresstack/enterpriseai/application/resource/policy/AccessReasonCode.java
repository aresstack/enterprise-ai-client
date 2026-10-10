package com.aresstack.enterpriseai.application.resource.policy;

/**
 * Stable reason codes for access decisions. Do not rely only on free text.
 */
public enum AccessReasonCode {
    ALLOWED,
    BLACKLISTED,
    NOT_WHITELISTED,
    BOT_RESTRICTED,
    SOURCE_AUTH_REQUIRED,
    SOURCE_DENIED,
    CACHE_ONLY_ALLOWED,
    TOO_LARGE,
    UNSUPPORTED_CONTENT_TYPE,
    NOT_VISIBLE_TO_ACTOR,
    UNKNOWN_RESOURCE,
    /** The requested operation does not match the service method invoked. */
    INVALID_OPERATION
}
