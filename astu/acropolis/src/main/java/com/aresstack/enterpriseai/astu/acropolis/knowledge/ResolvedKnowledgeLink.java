package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;

/**
 * Eine direkt aufgelöste Adresse: konkrete Quelle, stabile Ressourcen-ID, ihre kanonische Bookmark-Adresse und das
 * Fragment der Adresse (Abschnitt der Seite, z. B. für den Lesebereich).
 */
public final class ResolvedKnowledgeLink {

    private final SourceAddress sourceId;
    private final KnowledgeResourceId resourceId;
    private final String address;

    public ResolvedKnowledgeLink(SourceAddress sourceId, KnowledgeResourceId resourceId, String address) {
        if (sourceId == null || resourceId == null) {
            throw new IllegalArgumentException("sourceId and resourceId must not be null");
        }
        this.sourceId = sourceId;
        this.resourceId = resourceId;
        this.address = address == null ? "" : address.trim();
    }

    public SourceAddress sourceId() {
        return sourceId;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public BookmarkUri bookmark() {
        return KnowledgeBookmarks.of(resourceId);
    }

    /** Die vom Benutzer eingegebene Adresse, getrimmt. */
    public String address() {
        return address;
    }

    /** Das Fragment der Adresse (hinter {@code #}, roh, ohne {@code #}); leer ohne. */
    public String fragment() {
        int hash = address.indexOf('#');
        return hash < 0 ? "" : address.substring(hash + 1);
    }

    @Override
    public String toString() {
        return "ResolvedKnowledgeLink{" + sourceId + " -> " + resourceId
                + (fragment().isEmpty() ? "" : " #" + fragment()) + "}";
    }
}
