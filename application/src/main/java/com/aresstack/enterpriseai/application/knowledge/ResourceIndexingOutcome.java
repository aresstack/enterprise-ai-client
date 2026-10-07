package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;

/**
 * Was mit einer Ressource in einem Indexierungslauf geschah. Bei {@link IndexingStatus#FAILED} nennen
 * {@link #stage()} und {@link #message()} den gescheiterten Schritt; die Meldung stammt aus den Port-Ausnahmen,
 * die laut Vertrag keine Zugangsdaten enthalten.
 */
public final class ResourceIndexingOutcome {

    private final KnowledgeResourceId resourceId;
    private final String title;
    private final IndexingStatus status;
    private final int chunkCount;
    private final IndexingStage stage;
    private final String message;

    ResourceIndexingOutcome(KnowledgeResourceId resourceId, String title, IndexingStatus status, int chunkCount,
                            IndexingStage stage, String message) {
        this.resourceId = resourceId;
        this.title = title == null ? "" : title;
        this.status = status;
        this.chunkCount = chunkCount;
        this.stage = stage;
        this.message = message == null ? "" : message;
    }

    static ResourceIndexingOutcome done(KnowledgeResourceId id, String title, IndexingStatus status, int chunks) {
        return new ResourceIndexingOutcome(id, title, status, chunks, null, null);
    }

    static ResourceIndexingOutcome failed(KnowledgeResourceId id, String title, IndexingStage stage, String message) {
        return new ResourceIndexingOutcome(id, title, IndexingStatus.FAILED, 0, stage, message);
    }

    /** Die indexierte Ressource; nach einer Weiterleitung das Ziel. */
    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public String title() {
        return title;
    }

    public IndexingStatus status() {
        return status;
    }

    /** Anzahl der geschriebenen Chunks (nur bei {@link IndexingStatus#INDEXED} größer 0). */
    public int chunkCount() {
        return chunkCount;
    }

    /** Gescheiterter Schritt; {@code null}, wenn nicht {@link IndexingStatus#FAILED}. */
    public IndexingStage stage() {
        return stage;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "ResourceIndexingOutcome{" + resourceId + ", " + status
                + (status == IndexingStatus.FAILED ? " at " + stage + ": " + message : ", chunks=" + chunkCount) + "}";
    }
}
