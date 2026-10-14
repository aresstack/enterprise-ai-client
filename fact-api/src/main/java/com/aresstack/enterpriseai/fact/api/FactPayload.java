package com.aresstack.enterpriseai.fact.api;

/**
 * The structured content of a fact as JSON object text, e.g. {@code {"acronym":"IAR","expansion":"..."}}. Nesting is
 * free; the type's schema version tells how to read it. Only the outer shape is checked here; the store rejects text
 * that is no valid JSON with {@link FactStoreException.Reason#INVALID_PAYLOAD}. A store may normalize whitespace and
 * number notation, so compare payloads read back from the store.
 */
public final class FactPayload {

    private final String json;

    private FactPayload(String json) {
        this.json = json;
    }

    /** @throws IllegalArgumentException unless the trimmed text starts with '{' and ends with '}' */
    public static FactPayload json(String json) {
        String text = json == null ? "" : json.trim();
        if (text.length() < 2 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            throw new IllegalArgumentException("fact payload must be a JSON object");
        }
        return new FactPayload(text);
    }

    public String json() {
        return json;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactPayload && json.equals(((FactPayload) other).json);
    }

    @Override
    public int hashCode() {
        return json.hashCode();
    }

    @Override
    public String toString() {
        return json;
    }
}
