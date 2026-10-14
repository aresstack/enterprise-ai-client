package com.aresstack.enterpriseai.fact.api;

/**
 * Entry of the runtime type catalog: identity, description and the current schema version of its payloads, plus an
 * optional declarative JSON schema and the optional {@link ArgumentProjection} that makes payload members searchable
 * (issue #108). The store keeps the schema text but does not enforce it.
 *
 * <p>Registering a type again with the same version, schema and projection is a no-op (the description may change);
 * a higher version replaces the definition (and re-derives the arguments of its facts); a lower version, or another
 * schema or projection under the same version, is rejected with {@link FactStoreException.Reason#INCOMPATIBLE_SCHEMA}.
 * Facts written under older versions stay readable.
 */
public final class FactType {

    /** Longest description; matches the column width in every store. */
    public static final int MAX_DESCRIPTION_LENGTH = 4000;

    private final FactTypeId id;
    private final String description;
    private final int schemaVersion;
    private final String jsonSchema;
    private final ArgumentProjection argumentProjection;

    private FactType(FactTypeId id, String description, int schemaVersion, String jsonSchema,
                     ArgumentProjection argumentProjection) {
        if (id == null) {
            throw new IllegalArgumentException("fact type id must not be null");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schema version must be at least 1: " + schemaVersion);
        }
        String text = description == null ? "" : description.trim();
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("fact type description longer than " + MAX_DESCRIPTION_LENGTH);
        }
        this.id = id;
        this.description = text;
        this.schemaVersion = schemaVersion;
        this.jsonSchema = jsonSchema == null || jsonSchema.trim().isEmpty() ? "" : jsonSchema.trim();
        this.argumentProjection = argumentProjection == null ? ArgumentProjection.none() : argumentProjection;
    }

    /** A type in schema version 1 without declarative schema. */
    public static FactType of(FactTypeId id, String description) {
        return new FactType(id, description, 1, null, null);
    }

    /**
     * @param jsonSchema declarative JSON schema of the payloads, or {@code null}/empty for none
     */
    public static FactType of(FactTypeId id, String description, int schemaVersion, String jsonSchema) {
        return new FactType(id, description, schemaVersion, jsonSchema, null);
    }

    /**
     * @param jsonSchema         declarative JSON schema of the payloads, or {@code null}/empty for none
     * @param argumentProjection searchable arguments of the payloads, or {@code null} for none
     */
    public static FactType of(FactTypeId id, String description, int schemaVersion, String jsonSchema,
                              ArgumentProjection argumentProjection) {
        return new FactType(id, description, schemaVersion, jsonSchema, argumentProjection);
    }

    public FactTypeId id() {
        return id;
    }

    public String description() {
        return description;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** The declarative JSON schema, or {@code ""} if none was given. */
    public String jsonSchema() {
        return jsonSchema;
    }

    /** The searchable arguments of the payloads; {@link ArgumentProjection#none()} if there are none. */
    public ArgumentProjection argumentProjection() {
        return argumentProjection;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FactType)) {
            return false;
        }
        FactType that = (FactType) other;
        return id.equals(that.id) && description.equals(that.description) && schemaVersion == that.schemaVersion
                && jsonSchema.equals(that.jsonSchema) && argumentProjection.equals(that.argumentProjection);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * id.hashCode() + schemaVersion) + jsonSchema.hashCode();
    }

    @Override
    public String toString() {
        return "FactType[" + id + " v" + schemaVersion + "]";
    }
}
