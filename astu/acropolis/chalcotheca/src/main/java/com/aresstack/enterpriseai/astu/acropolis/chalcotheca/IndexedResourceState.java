package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;

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
    /**
     * Metadaten-Schlüssel des Fingerabdrucks der Zerlegung (Passagen-Verfahren, Parameter, Satzmodell). Zusammen mit
     * dem Namensraum (Embedding-Modell) und dem Inhalts-Hash entscheidet er, ob neu verarbeitet werden muss.
     */
    public static final String SEGMENTATION_KEY = "index.segmentation";

    private final KnowledgeRevision revision;
    private final String contentHash;
    private final String segmentation;

    public IndexedResourceState(KnowledgeRevision revision, String contentHash) {
        this(revision, contentHash, "");
    }

    public IndexedResourceState(KnowledgeRevision revision, String contentHash, String segmentation) {
        if (revision == null) {
            throw new IllegalArgumentException("revision must not be null");
        }
        this.revision = revision;
        this.contentHash = contentHash == null ? "" : contentHash;
        this.segmentation = segmentation == null ? "" : segmentation;
    }

    /** Fingerabdruck der Zerlegung, mit der indexiert wurde; leer bei alten Einträgen. */
    public String segmentation() {
        return segmentation;
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
        return "IndexedResourceState{" + revision + ", contentHash=" + (contentHash.isEmpty() ? "-" : contentHash)
                + ", segmentation=" + (segmentation.isEmpty() ? "-" : segmentation) + "}";
    }
}
