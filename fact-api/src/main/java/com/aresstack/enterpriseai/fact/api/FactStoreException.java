package com.aresstack.enterpriseai.fact.api;

/** A fact store operation was rejected or failed; {@link #reason()} says why. Nothing was changed. */
public class FactStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the operation did not happen. */
    public enum Reason {
        /** The fact names a type that is not registered. */
        UNKNOWN_TYPE,
        /** The fact names a resource that is not registered. */
        UNKNOWN_RESOURCE,
        /** The payload is no valid JSON object or too large. */
        INVALID_PAYLOAD,
        /** A type registration or a fact's schema version does not fit the registered type. */
        INCOMPATIBLE_SCHEMA,
        /** The fact id exists with another resource, or a concurrent write got there first. */
        CONFLICT,
        /** Another transaction held the lock longer than the configured lock timeout. */
        LOCK_TIMEOUT,
        /** The storage could not be opened, read or written, or the store is closed. */
        STORAGE
    }

    private final Reason reason;

    public FactStoreException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FactStoreException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
