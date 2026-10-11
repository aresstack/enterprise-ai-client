package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;

/**
 * Was der Index von einer Ressource weiß (je Namespace): die Revision der Quelle und der {@code contentHash} des
 * indexierten Textes (SHA-256 aus {@code KnowledgeDocument#contentHash()}). Damit entscheidet die suchgesteuerte
 * Indexierung, ob ein Treffer neu zerlegt und eingebettet werden muss (wie MainframeMate: Suche füllt den Index,
 * unveränderte Seiten werden nie neu verarbeitet).
 *
 * <p>Der Hash liegt in den Metadaten der indexierten Ressource unter {@link #CONTENT_HASH_KEY}; Einträge aus der Zeit
 * vor dieser Klasse haben keinen ({@link #contentHash()} leer).
 */
public final class IndexedResourceState {

    /** Metadaten-Schlüssel, unter dem die Indexierung den Inhalts-Hash an der Ressource ablegt. */
    public static final String CONTENT_HASH_KEY = "index.contentHash";

    private final KnowledgeRevision revision;
    private final String contentHash;

    public IndexedResourceState(KnowledgeRevision revision, String contentHash) {
        if (revision == null) {
            throw new IllegalArgumentException("revision must not be null");
        }
        this.revision = revision;
        this.contentHash = contentHash == null ? "" : contentHash;
    }

    public KnowledgeRevision revision() {
        return revision;
    }

    /** SHA-256 (hex) des indexierten Textes; leer, wenn unbekannt. */
    public String contentHash() {
        return contentHash;
    }

    public boolean hasContentHash() {
        return !contentHash.isEmpty();
    }

    @Override
    public String toString() {
        return "IndexedResourceState{" + revision + ", contentHash=" + (contentHash.isEmpty() ? "-" : contentHash) + "}";
    }
}
