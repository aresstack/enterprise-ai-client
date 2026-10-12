package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;

/**
 * Ein geladenes Dokument, wie es der Rohindex ({@link RawKnowledgeIndexPort}) aufnimmt: Ressource (ID, Quelle,
 * Titel, URL, Revision), der ganze extrahierte Text, sein Inhalts-Hash und das Snippet der Quellensuche. Keine
 * Passagen, kein Embedding: der Rohindex ist die schnelle Stufe, die eine Seite Millisekunden nach dem Laden
 * durchsuchbar macht; die semantische Aufbereitung ({@link KnowledgeIndexPort}) folgt unabhängig davon.
 */
public final class RawKnowledgeDocument {

    private final KnowledgeResource resource;
    private final String text;
    private final String contentHash;
    private final String snippet;

    private RawKnowledgeDocument(KnowledgeResource resource, String text, String contentHash, String snippet) {
        this.resource = resource;
        this.text = text;
        this.contentHash = contentHash;
        this.snippet = snippet;
    }

    /** Aus einem geladenen Dokument; {@code snippet} ist das Snippet der Quellensuche oder leer. */
    public static RawKnowledgeDocument of(KnowledgeDocument document, String snippet) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        return new RawKnowledgeDocument(document.resource(), document.text(), document.contentHash(),
                snippet == null ? "" : snippet.trim());
    }

    /** Wiederhergestellt aus einem Index (alle Teile wie gespeichert). */
    public static RawKnowledgeDocument stored(KnowledgeResource resource, String text, String contentHash,
                                              String snippet) {
        if (resource == null || text == null || contentHash == null) {
            throw new IllegalArgumentException("resource, text and contentHash must not be null");
        }
        return new RawKnowledgeDocument(resource, text, contentHash, snippet == null ? "" : snippet);
    }

    public KnowledgeResource resource() {
        return resource;
    }

    public KnowledgeResourceId id() {
        return resource.id();
    }

    public String text() {
        return text;
    }

    /** SHA-256 des Textes ({@code KnowledgeDocument#contentHash()}). */
    public String contentHash() {
        return contentHash;
    }

    /** Snippet der Quellensuche; leer, wenn die Seite nicht über eine Suche kam. */
    public String snippet() {
        return snippet;
    }

    @Override
    public String toString() {
        return "RawKnowledgeDocument{" + resource.id() + ", " + text.length() + " Zeichen}";
    }
}
