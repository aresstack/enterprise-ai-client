package com.aresstack.enterpriseai.fact.api;

/**
 * Optional explicit role of a fact for its resource, e.g. {@code DEFINES} (the resource defines the acronym) versus
 * {@code MENTIONS} (it only uses it). Never derived from the fact type; set by whoever writes the fact.
 */
public final class FactRole {

    private final String value;

    private FactRole(String value) {
        this.value = value;
    }

    /** The resource defines what the fact states. */
    public static FactRole defines() {
        return new FactRole("DEFINES");
    }

    /** The resource only mentions what the fact states. */
    public static FactRole mentions() {
        return new FactRole("MENTIONS");
    }

    /** @throws IllegalArgumentException unless {@code [A-Z][A-Z0-9_]*} with at most 64 characters */
    public static FactRole of(String value) {
        return new FactRole(CatalogName.check("fact role", value));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactRole && value.equals(((FactRole) other).value);
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
