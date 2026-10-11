package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;

/**
 * Eine Passage der Index-Pipeline für genau die geöffnete Fassung: ihre Passage-ID ({@code KnowledgePassageId}), ihr
 * Text und ihr eigenes Embedding. Reihenfolge = Lesereihenfolge.
 */
public final class NavigationPassage {

    private final String id;
    private final String text;
    private final EmbeddingVector vector;

    public NavigationPassage(String id, String text, EmbeddingVector vector) {
        if (id == null || id.isEmpty() || text == null || vector == null) {
            throw new IllegalArgumentException("id, text und vector sind Pflicht");
        }
        this.id = id;
        this.text = text;
        this.vector = vector;
    }

    public String id() {
        return id;
    }

    public String text() {
        return text;
    }

    public EmbeddingVector vector() {
        return vector;
    }
}
