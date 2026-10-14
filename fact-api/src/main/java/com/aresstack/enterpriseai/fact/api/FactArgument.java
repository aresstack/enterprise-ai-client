package com.aresstack.enterpriseai.fact.api;

/**
 * One searchable participant of a fact (issue #108), derived from its payload by the type's
 * {@link ArgumentProjection}: a role ({@code ACTOR}, {@code AFFECTED_OBJECT}, {@code PREDICATE}, ...), an ordinal for
 * repeated roles, and a reference to another fact of the same resource (e.g. the concrete mention) and/or a
 * normalized search value. The normalized value is a search term, never an identity: equal values from different
 * documents do not denote the same thing.
 */
public final class FactArgument {

    /** Longest normalized value; matches the column width in every store. */
    public static final int MAX_VALUE_LENGTH = 512;

    private final String role;
    private final int ordinal;
    private final FactId valueFactId;
    private final String normalizedValue;

    private FactArgument(String role, int ordinal, FactId valueFactId, String normalizedValue) {
        this.role = CatalogName.check("argument role", role);
        if (ordinal < 0) {
            throw new IllegalArgumentException("argument ordinal must not be negative: " + ordinal);
        }
        String value = normalizedValue == null ? "" : normalizedValue.trim();
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("normalized argument value longer than " + MAX_VALUE_LENGTH);
        }
        if (valueFactId == null && value.isEmpty()) {
            throw new IllegalArgumentException("argument needs a referenced fact or a normalized value");
        }
        this.ordinal = ordinal;
        this.valueFactId = valueFactId;
        this.normalizedValue = value.isEmpty() ? null : value;
    }

    /**
     * @param valueFactId     referenced fact on the same resource, or {@code null} for a literal
     * @param normalizedValue search value, or {@code null}; at least one of both is needed
     */
    public static FactArgument of(String role, int ordinal, FactId valueFactId, String normalizedValue) {
        return new FactArgument(role, ordinal, valueFactId, normalizedValue);
    }

    public String role() {
        return role;
    }

    public int ordinal() {
        return ordinal;
    }

    /** The referenced fact, or {@code null} for a literal. */
    public FactId valueFactId() {
        return valueFactId;
    }

    /** The normalized search value, or {@code null}. */
    public String normalizedValue() {
        return normalizedValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FactArgument)) {
            return false;
        }
        FactArgument that = (FactArgument) other;
        return role.equals(that.role) && ordinal == that.ordinal
                && (valueFactId == null ? that.valueFactId == null : valueFactId.equals(that.valueFactId))
                && (normalizedValue == null ? that.normalizedValue == null
                : normalizedValue.equals(that.normalizedValue));
    }

    @Override
    public int hashCode() {
        return 31 * (31 * role.hashCode() + ordinal) + (normalizedValue == null ? 0 : normalizedValue.hashCode());
    }

    @Override
    public String toString() {
        return role + "#" + ordinal + "=" + (valueFactId == null ? "" : valueFactId + "/")
                + (normalizedValue == null ? "" : normalizedValue);
    }
}
