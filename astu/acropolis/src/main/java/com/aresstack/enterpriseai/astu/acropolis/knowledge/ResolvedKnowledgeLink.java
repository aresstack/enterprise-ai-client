package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;

/** Eine direkt aufgelöste Benutzeradresse: konkrete Quelle, stabile Ressourcen-ID und ihre kanonische Bookmark-Adresse. */
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

    @Override
    public String toString() {
        return "ResolvedKnowledgeLink{" + sourceId + " -> " + resourceId + "}";
    }
}
