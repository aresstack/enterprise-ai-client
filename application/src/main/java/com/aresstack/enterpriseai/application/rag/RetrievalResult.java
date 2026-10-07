package com.aresstack.enterpriseai.application.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ergebnis von {@link RetrieveKnowledgeUseCase}: die fusionierten Treffer in Relevanzreihenfolge und Warnungen zu
 * ausgefallenen Suchpfaden. Ist ein Pfad ausgefallen, ist das Ergebnis vollständig aus dem anderen Pfad, aber
 * nicht hybrid; die Oberfläche kann das über {@link #isDegraded()} anzeigen.
 */
public final class RetrievalResult {

    private final List<RetrievedChunk> hits;
    private final List<RetrievalWarning> warnings;

    RetrievalResult(List<RetrievedChunk> hits, List<RetrievalWarning> warnings) {
        this.hits = Collections.unmodifiableList(new ArrayList<RetrievedChunk>(hits));
        this.warnings = Collections.unmodifiableList(new ArrayList<RetrievalWarning>(warnings));
    }

    static RetrievalResult empty() {
        return new RetrievalResult(Collections.<RetrievedChunk>emptyList(), Collections.<RetrievalWarning>emptyList());
    }

    /** Treffer absteigend nach RRF-Score, je Chunk höchstens einmal. */
    public List<RetrievedChunk> hits() {
        return hits;
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    public List<RetrievalWarning> warnings() {
        return warnings;
    }

    /** Ein aktiver Suchpfad ist ausgefallen. */
    public boolean isDegraded() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return "RetrievalResult{hits=" + hits.size() + ", warnings=" + warnings + "}";
    }
}
