package com.aresstack.enterpriseai.domain.knowledge;

/**
 * Deterministische Identität eines Chunks: {@code <resourceId>#chunk-<ordinal>}. Dieselbe Ressource mit
 * demselben Inhalt und derselben {@link KnowledgeChunkingPolicy} ergibt dieselben IDs; Upsert im Index ist damit
 * idempotent. Alte Chunks einer Ressource ersetzt der Index ohnehin vollständig pro Ressource.
 */
public final class KnowledgeChunkId {

    private static final String SEPARATOR = "#chunk-";

    private final KnowledgeResourceId resourceId;
    private final int ordinal;
    private final String value;

    private KnowledgeChunkId(KnowledgeResourceId resourceId, int ordinal) {
        this.resourceId = resourceId;
        this.ordinal = ordinal;
        this.value = resourceId.value() + SEPARATOR + ordinal;
    }

    public static KnowledgeChunkId of(KnowledgeResourceId resourceId, int ordinal) {
        if (resourceId == null) {
            throw new IllegalArgumentException("Resource-ID fehlt");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal darf nicht negativ sein: " + ordinal);
        }
        return new KnowledgeChunkId(resourceId, ordinal);
    }

    /** Liest eine mit {@link #value()} erzeugte ID wieder ein (z. B. aus einem persistierten Index). */
    public static KnowledgeChunkId parse(String value) {
        int separator = value == null ? -1 : value.lastIndexOf(SEPARATOR);
        if (separator <= 0) {
            throw new IllegalArgumentException("Ungültige Chunk-ID: " + KnowledgeSourceId.quote(value));
        }
        String ordinal = value.substring(separator + SEPARATOR.length());
        if (ordinal.isEmpty() || ordinal.length() > 9 || !ordinal.matches("[0-9]+")
                || ordinal.length() > 1 && ordinal.charAt(0) == '0') {
            throw new IllegalArgumentException("Ungültige Chunk-ID: " + KnowledgeSourceId.quote(value));
        }
        return of(KnowledgeResourceId.of(value.substring(0, separator)), Integer.parseInt(ordinal));
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public int ordinal() {
        return ordinal;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KnowledgeChunkId && value.equals(((KnowledgeChunkId) other).value);
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
