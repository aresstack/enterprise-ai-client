package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

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

    private final SourceAddress sourceId;
    private final KnowledgeResourceId resourceId;
    private final String title;
    private final String snippet;
    private final String location;
    private final Origin origin;
    private final KnowledgeRevision revision;
    private final String url;

    public FoundResource(SourceAddress sourceId, KnowledgeResourceId resourceId, String title, String snippet,
                         String location, Origin origin) {
        this(sourceId, resourceId, title, snippet, location, origin, KnowledgeRevision.unknown());
    }

    /** Wie oben, mit der Revision laut Suche (für das Vorladen: unverändert indexierte Treffer werden nicht geladen). */
    public FoundResource(SourceAddress sourceId, KnowledgeResourceId resourceId, String title, String snippet,
                         String location, Origin origin, KnowledgeRevision revision) {
        this(sourceId, resourceId, title, snippet, location, origin, revision, null);
    }

    /** Wie oben, mit der aufrufbaren Adresse (Bookmark) laut Suche. */
    public FoundResource(SourceAddress sourceId, KnowledgeResourceId resourceId, String title, String snippet,
                         String location, Origin origin, KnowledgeRevision revision, String url) {
        if (sourceId == null || resourceId == null || origin == null) {
            throw new IllegalArgumentException("sourceId, resourceId and origin must not be null");
        }
        this.sourceId = sourceId;
        this.resourceId = resourceId;
        this.title = title == null || title.trim().isEmpty() ? resourceId.schemeSpecificPart() : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.location = location == null || location.trim().isEmpty() ? resourceId.value() : location.trim();
        this.origin = origin;
        this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
        this.url = url == null || url.trim().isEmpty() ? null : url.trim();
    }

    /** Aufrufbare Adresse (Bookmark); {@code null}, wenn die Suche keine nennt. */
    public String url() {
        return url;
    }

    /** Die Revision laut Suche; {@link KnowledgeRevision#unknown()}, wenn die Suche keine nennt. */
    public KnowledgeRevision revision() {
        return revision;
    }

    public SourceAddress sourceId() {
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
