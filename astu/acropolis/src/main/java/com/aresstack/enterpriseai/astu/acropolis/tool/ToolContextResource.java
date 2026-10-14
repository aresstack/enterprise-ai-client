package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;

/**
 * A knowledge resource whose content a tool call handed to the model as context, so the answer can name it as a
 * used source (provenance). Carries stable identities, never the content itself.
 */
public final class ToolContextResource {

    private final String sourceId;
    private final String resourceId;
    private final String title;
    private final String location;
    private final KnowledgeRevision revision;

    /**
     * @param sourceId   id of the knowledge source (not blank)
     * @param resourceId stable id of the resource (not blank)
     * @param title      title; blank falls back to {@code resourceId}
     * @param location   browser address or {@code null}/blank if none is known
     * @param revision   revision or {@code null} if unknown
     */
    public ToolContextResource(String sourceId, String resourceId, String title, String location,
                               KnowledgeRevision revision) {
        if (sourceId == null || sourceId.trim().isEmpty() || resourceId == null || resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId and resourceId must not be blank");
        }
        this.sourceId = sourceId.trim();
        this.resourceId = resourceId.trim();
        this.title = title == null || title.trim().isEmpty() ? this.resourceId : title.trim();
        this.location = location == null ? "" : location.trim();
        this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
    }

    public String sourceId() {
        return sourceId;
    }

    public String resourceId() {
        return resourceId;
    }

    public String title() {
        return title;
    }

    /** Browser address or empty. */
    public String location() {
        return location;
    }

    public KnowledgeRevision revision() {
        return revision;
    }

    @Override
    public String toString() {
        // Keep titles and addresses out of logs.
        return "ToolContextResource{" + sourceId + '}';
    }
}
