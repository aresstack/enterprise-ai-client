package com.aresstack.enterpriseai.domain.knowledge;

import java.net.URI;

/**
 * Bookmark einer Ressource (corenth {@code BookmarkUri} + MainframeMate {@code BookmarkEntry}): woher
 * ({@code sourceId}), welche ({@code resourceId}, z. B. {@code confluence:47110815}, der stabile technische Schlüssel),
 * wo man sie öffnet ({@code canonicalUrl}, Navigation und Provenienz) und in welchem Stand ({@code revision},
 * {@code contentHash}). Wer eine Ressource später wieder braucht, merkt sich nur diese Referenz; der Inhalt steht
 * aufbereitet im Index und wird nur bei unbekanntem oder geändertem Stand neu aus der Quelle geholt.
 */
public final class KnowledgeResourceRef {

    private final KnowledgeSourceId sourceId;
    private final KnowledgeResourceId resourceId;
    private final URI canonicalUrl;
    private final KnowledgeRevision revision;
    private final String contentHash;

    public KnowledgeResourceRef(KnowledgeSourceId sourceId, KnowledgeResourceId resourceId, URI canonicalUrl,
                                KnowledgeRevision revision, String contentHash) {
        if (sourceId == null || resourceId == null || revision == null) {
            throw new IllegalArgumentException("sourceId, resourceId und revision sind Pflicht");
        }
        this.sourceId = sourceId;
        this.resourceId = resourceId;
        this.canonicalUrl = canonicalUrl;
        this.revision = revision;
        this.contentHash = contentHash == null ? "" : contentHash;
    }

    /** Referenz auf eine Ressource, deren Inhalt schon geladen ist. */
    public static KnowledgeResourceRef of(KnowledgeDocument document) {
        KnowledgeResource resource = document.resource();
        return new KnowledgeResourceRef(resource.sourceId(), resource.id(), resource.location().orElse(null),
                resource.revision(), document.contentHash());
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    /** Die URL zum Öffnen; {@code null}, wenn die Quelle keine kennt. */
    public URI canonicalUrl() {
        return canonicalUrl;
    }

    public KnowledgeRevision revision() {
        return revision;
    }

    /** SHA-256 des Textes (wie {@link KnowledgeDocument#contentHash()}); leer, wenn unbekannt. */
    public String contentHash() {
        return contentHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KnowledgeResourceRef)) {
            return false;
        }
        KnowledgeResourceRef that = (KnowledgeResourceRef) other;
        return resourceId.equals(that.resourceId) && revision.equals(that.revision)
                && contentHash.equals(that.contentHash);
    }

    @Override
    public int hashCode() {
        return resourceId.hashCode() * 31 + contentHash.hashCode();
    }

    @Override
    public String toString() {
        return "KnowledgeResourceRef{" + resourceId + ", " + revision + ", url=" + canonicalUrl + "}";
    }
}
