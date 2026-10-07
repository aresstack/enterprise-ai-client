package com.aresstack.enterpriseai.domain.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein zusammenhängender Textabschnitt einer Ressource – die Einheit, die vektorisiert, indexiert und als
 * Quelle in den RAG-Kontext gegeben wird.
 *
 * <p>{@link #text()} ist der Abschnitt selbst, {@link #headingPath()} die Überschriftenkette, unter der er steht
 * (z. B. {@code [Installation, Linux]}). Für Embedding und Prompt liefert {@link #textWithHeading()} beides
 * zusammen. {@link #tokenCount()} ist die Größe von {@link #textWithHeading()} laut dem Token-Zähler des Chunkers.
 */
public final class KnowledgeChunk {

    private final KnowledgeChunkId id;
    private final KnowledgeSourceId sourceId;
    private final List<String> headingPath;
    private final String text;
    private final int tokenCount;

    public KnowledgeChunk(KnowledgeChunkId id, KnowledgeSourceId sourceId, List<String> headingPath, String text,
                          int tokenCount) {
        if (id == null) {
            throw new IllegalArgumentException("Chunk-ID fehlt");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("Source-ID fehlt");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk " + id + " hat keinen Text");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount darf nicht negativ sein");
        }
        List<String> path = new ArrayList<String>();
        if (headingPath != null) {
            for (String heading : headingPath) {
                if (heading == null || heading.trim().isEmpty()) {
                    throw new IllegalArgumentException("Leere Überschrift im headingPath von " + id);
                }
                path.add(heading);
            }
        }
        this.id = id;
        this.sourceId = sourceId;
        this.headingPath = Collections.unmodifiableList(path);
        this.text = text;
        this.tokenCount = tokenCount;
    }

    public KnowledgeChunkId id() {
        return id;
    }

    public KnowledgeResourceId resourceId() {
        return id.resourceId();
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    /** Position innerhalb der Ressource, ab 0, lückenlos. */
    public int ordinal() {
        return id.ordinal();
    }

    public List<String> headingPath() {
        return headingPath;
    }

    /** Überschriftenkette als eine Zeile, z. B. {@code Installation > Linux}; {@code ""} ohne Überschrift. */
    public String headingLine() {
        StringBuilder line = new StringBuilder();
        for (String heading : headingPath) {
            if (line.length() > 0) {
                line.append(" > ");
            }
            line.append(heading);
        }
        return line.toString();
    }

    public String text() {
        return text;
    }

    /** Überschriftenzeile, Leerzeile, Text – die Fassung für Embedding und Prompt-Kontext. */
    public String textWithHeading() {
        return headingPath.isEmpty() ? text : headingLine() + "\n\n" + text;
    }

    public int tokenCount() {
        return tokenCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KnowledgeChunk)) {
            return false;
        }
        KnowledgeChunk that = (KnowledgeChunk) other;
        return id.equals(that.id) && sourceId.equals(that.sourceId) && headingPath.equals(that.headingPath)
                && text.equals(that.text) && tokenCount == that.tokenCount;
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + text.hashCode();
    }

    /** Ohne Volltext. */
    @Override
    public String toString() {
        return "KnowledgeChunk{" + id + ", heading='" + headingLine() + "', tokens=" + tokenCount + ", chars="
                + text.length() + "}";
    }
}
