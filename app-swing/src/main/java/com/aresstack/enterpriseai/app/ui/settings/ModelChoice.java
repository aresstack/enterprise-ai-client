package com.aresstack.enterpriseai.app.ui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Modell aus {@code GET /models}: Kennung, ob der Dienst es als Tool-Calling-fähig meldet, und seine
 * Fähigkeiten ({@code capabilities}). Meldet der Dienst keine Fähigkeiten, gilt das Modell für Chat und
 * Embeddings als möglich. Die Kennung bleibt reiner Konfigurationswert.
 */
public final class ModelChoice {

    private final String id;
    private final boolean toolCalling;
    private final List<String> capabilities;

    public ModelChoice(String id, boolean toolCalling, List<String> capabilities) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.toolCalling = toolCalling;
        this.capabilities = capabilities == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(capabilities));
    }

    public String id() {
        return id;
    }

    public boolean toolCalling() {
        return toolCalling;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public boolean suitableForChat() {
        return capabilities.isEmpty() || capabilities.contains("completion") || capabilities.contains("chat");
    }

    public boolean suitableForEmbedding() {
        return capabilities.isEmpty() || capabilities.contains("embeddings") || capabilities.contains("embedding");
    }

    /** Anzeige in der Auswahlliste. */
    @Override
    public String toString() {
        return toolCalling ? id + "  (Tool-Calling)" : id;
    }
}
