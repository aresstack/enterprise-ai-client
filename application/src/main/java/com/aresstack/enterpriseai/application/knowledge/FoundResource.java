package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

/**
 * Ein Treffer der Quellensuche ({@link SearchKnowledgeSourcesUseCase}): welche Ressource welcher Quelle, ihr Titel, ein
 * kurzer Klartext-Auszug und die Fundstelle (die Ressourcen-ID, bei Index-Treffern samt Abschnitt). Unveränderlich.
 */
public final class FoundResource {

    /** Woher der Treffer stammt: aus der Suche der Quelle selbst oder aus dem Wissensindex. */
    public enum Origin {
        SOURCE,
        INDEX
    }

    private final KnowledgeSourceId sourceId;
    private final KnowledgeResourceId resourceId;
    private final String title;
    private final String snippet;
    private final String location;
    private final Origin origin;

    public FoundResource(KnowledgeSourceId sourceId, KnowledgeResourceId resourceId, String title, String snippet,
                         String location, Origin origin) {
        if (sourceId == null || resourceId == null || origin == null) {
            throw new IllegalArgumentException("sourceId, resourceId and origin must not be null");
        }
        this.sourceId = sourceId;
        this.resourceId = resourceId;
        this.title = title == null || title.trim().isEmpty() ? resourceId.schemeSpecificPart() : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.location = location == null || location.trim().isEmpty() ? resourceId.value() : location.trim();
        this.origin = origin;
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public String title() {
        return title;
    }

    /** Klartext ohne Markup; leer, wenn es keinen Auszug gibt. */
    public String snippet() {
        return snippet;
    }

    /** Die Fundstelle, z. B. {@code wiki:intranet/Handbuch › Installation}. */
    public String location() {
        return location;
    }

    public Origin origin() {
        return origin;
    }

    @Override
    public String toString() {
        return "FoundResource{" + resourceId + ", '" + title + "', " + origin + "}";
    }
}
