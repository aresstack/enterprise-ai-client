package com.aresstack.enterpriseai.fact.api;

import java.util.UUID;

/** Own identity of a fact (UUID), stable across edits; a later knowledge graph refers to it. */
public final class FactId {

    private final UUID value;

    private FactId(UUID value) {
        this.value = value;
    }

    public static FactId random() {
        return new FactId(UUID.randomUUID());
    }

    public static FactId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return new FactId(value);
    }

    /** @throws IllegalArgumentException if {@code value} is no UUID */
    public static FactId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return new FactId(UUID.fromString(value.trim()));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactId && value.equals(((FactId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
