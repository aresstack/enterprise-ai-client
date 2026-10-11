package com.aresstack.enterpriseai.source.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;

/** Treffer der quelleneigenen Suche: Ressource, Titel und ein kurzer Klartext-Ausschnitt. */
public final class SourceSearchHit {

    private final KnowledgeResourceId resourceId;
    private final String title;
    private final String snippet;
    private final KnowledgeRevision revision;

    public SourceSearchHit(KnowledgeResourceId resourceId, String title, String snippet) {
        this(resourceId, title, snippet, KnowledgeRevision.unknown());
    }

    /**
     * @param revision Stand der Seite, wenn die Suche ihn mitliefert (z. B. Confluence-Version); damit überspringt die
     *                 suchgesteuerte Indexierung unveränderte Treffer, ohne sie zu laden
     */
    public SourceSearchHit(KnowledgeResourceId resourceId, String title, String snippet, KnowledgeRevision revision) {
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId must not be null");
        }
        this.resourceId = resourceId;
        this.title = title == null ? "" : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
    }

    /** Stand laut Suche; {@link KnowledgeRevision#unknown()}, wenn die Quelle ihn nicht nennt. */
    public KnowledgeRevision revision() {
        return revision;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public String title() {
        return title;
    }

    /** Klartext ohne Markup; leer, wenn die Quelle keinen Ausschnitt liefert. */
    public String snippet() {
        return snippet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceSearchHit)) {
            return false;
        }
        SourceSearchHit that = (SourceSearchHit) o;
        return resourceId.equals(that.resourceId) && title.equals(that.title) && snippet.equals(that.snippet);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * resourceId.hashCode() + title.hashCode()) + snippet.hashCode();
    }

    @Override
    public String toString() {
        return "SourceSearchHit{" + resourceId + ", '" + title + "'}";
    }
}
