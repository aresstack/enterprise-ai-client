package com.aresstack.enterpriseai.astu.acropolis.textmining;

/**
 * A kind of finding an extractor produces: identity ({@code [A-Z][A-Z0-9_]*}), description and the version of its
 * fields. A store registers it at runtime; no store knows the types beforehand.
 */
public final class TextFindingType {

    private final String id;
    private final String description;
    private final int schemaVersion;

    public TextFindingType(String id, String description, int schemaVersion) {
        if (id == null || !id.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid finding type " + id);
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schema version must be at least 1");
        }
        this.id = id;
        this.description = description == null ? "" : description;
        this.schemaVersion = schemaVersion;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public String toString() {
        return id + " v" + schemaVersion;
    }
}
