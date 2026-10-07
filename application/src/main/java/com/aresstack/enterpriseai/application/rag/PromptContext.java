package com.aresstack.enterpriseai.application.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Der für eine Anfrage gebaute Kontextblock: der Text, der getrennt von der Nutzerfrage als System-Anteil in den
 * Prompt geht, und die darin aufgenommenen Quellen in Blockreihenfolge. Ohne Quellen ist der Text leer.
 */
public final class PromptContext {

    private final String text;
    private final List<RagSource> sources;
    private final int tokenCount;
    private final int omittedHits;

    PromptContext(String text, List<RagSource> sources, int tokenCount, int omittedHits) {
        this.text = text;
        this.sources = Collections.unmodifiableList(new ArrayList<RagSource>(sources));
        this.tokenCount = tokenCount;
        this.omittedHits = omittedHits;
    }

    static PromptContext empty(int omittedHits) {
        return new PromptContext("", Collections.<RagSource>emptyList(), 0, omittedHits);
    }

    /** Der Kontextblock; leer, wenn keine Quelle aufgenommen wurde. */
    public String text() {
        return text;
    }

    /** Die aufgenommenen Quellen, Nummer 1 zuerst. */
    public List<RagSource> sources() {
        return sources;
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /** Größe von {@link #text()} laut Token-Zähler der {@link ContextSettings}. */
    public int tokenCount() {
        return tokenCount;
    }

    /** Treffer, die wegen Budget oder Quellenzahl nicht aufgenommen wurden. */
    public int omittedHits() {
        return omittedHits;
    }

    @Override
    public String toString() {
        return "PromptContext{sources=" + sources.size() + ", tokens=" + tokenCount + ", omitted=" + omittedHits
                + "}";
    }
}
