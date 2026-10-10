package com.aresstack.enterpriseai.resource.holkas;

/**
 * Requested read mode for raw resource acquisition.
 */
public enum ResourceReadMode {
    /** Let the connector choose the safest default mode. */
    DEFAULT,

    /** Prefer text transfer semantics when the backend distinguishes them. */
    TEXT,

    /** Prefer byte-exact transfer semantics. */
    BINARY
}
