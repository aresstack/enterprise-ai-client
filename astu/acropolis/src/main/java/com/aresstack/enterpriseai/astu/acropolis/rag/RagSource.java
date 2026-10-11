package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;

/**
 * Eine Quelle, die in den Kontextblock aufgenommen wurde: ihre Nummer im Block ({@code [1]}, {@code [2]} ...) und
 * der fusionierte Treffer mit Ressource (Titel, Ort, Revision, Metadaten), Chunk (Position, Überschriften) und
 * den Scores beider Suchpfade. Für Quellenangaben in der Oberfläche.
 */
public final class RagSource {

    private final int number;
    private final RetrievedChunk hit;

    RagSource(int number, RetrievedChunk hit) {
        this.number = number;
        this.hit = hit;
    }

    /** Nummer im Kontextblock, ab 1; so zitiert das Modell die Quelle. */
    public int number() {
        return number;
    }

    public RetrievedChunk hit() {
        return hit;
    }

    public KnowledgeResource resource() {
        return hit.resource();
    }

    public KnowledgeChunk chunk() {
        return hit.chunk();
    }

    @Override
    public String toString() {
        return "RagSource{[" + number + "] " + hit.chunk().id() + "}";
    }
}
