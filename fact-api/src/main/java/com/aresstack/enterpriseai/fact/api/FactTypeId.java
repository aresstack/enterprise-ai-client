package com.aresstack.enterpriseai.fact.api;

/**
 * Identity of a fact type in the runtime catalog, e.g. {@code ACRONYM}, {@code DEFINITION}, {@code BUSINESS_RULE},
 * {@code DATA_STRUCTURE}. Deliberately no enum: new types are registered while the application runs.
 */
public final class FactTypeId implements Comparable<FactTypeId> {

    private final String value;

    private FactTypeId(String value) {
        this.value = value;
    }

    /** @throws IllegalArgumentException unless {@code [A-Z][A-Z0-9_]*} with at most 64 characters */
    public static FactTypeId of(String value) {
        return new FactTypeId(CatalogName.check("fact type", value));
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(FactTypeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactTypeId && value.equals(((FactTypeId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
