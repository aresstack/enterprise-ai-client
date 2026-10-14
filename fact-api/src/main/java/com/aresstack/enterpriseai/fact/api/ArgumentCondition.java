package com.aresstack.enterpriseai.fact.api;

/**
 * One condition of an argument search (issue #108): a fact has an argument in {@code role} with this normalized value
 * ({@link #value}) or referring to this fact ({@link #reference}). Several conditions must all hold for the same fact,
 * each by any of its arguments in that role.
 */
public final class ArgumentCondition {

    private final String role;
    private final String normalizedValue;
    private final FactId reference;

    private ArgumentCondition(String role, String normalizedValue, FactId reference) {
        this.role = CatalogName.check("argument role", role);
        this.normalizedValue = normalizedValue;
        this.reference = reference;
    }

    /** An argument in {@code role} whose normalized value equals {@code normalizedValue} exactly. */
    public static ArgumentCondition value(String role, String normalizedValue) {
        if (normalizedValue == null || normalizedValue.trim().isEmpty()) {
            throw new IllegalArgumentException("normalized value must not be empty");
        }
        return new ArgumentCondition(role, normalizedValue.trim(), null);
    }

    /** An argument in {@code role} that refers to the fact {@code reference}. */
    public static ArgumentCondition reference(String role, FactId reference) {
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        return new ArgumentCondition(role, null, reference);
    }

    public String role() {
        return role;
    }

    /** The required normalized value, or {@code null} for a reference condition. */
    public String normalizedValue() {
        return normalizedValue;
    }

    /** The required referenced fact, or {@code null} for a value condition. */
    public FactId reference() {
        return reference;
    }

    @Override
    public String toString() {
        return role + "=" + (reference == null ? normalizedValue : reference.toString());
    }
}
