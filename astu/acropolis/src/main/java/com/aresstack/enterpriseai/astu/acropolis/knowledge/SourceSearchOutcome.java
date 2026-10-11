package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeSourceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Das Ergebnis der Suche in EINER Quelle: ihre Treffer oder, wenn die Quelle nicht antwortete, das Problem. */
public final class SourceSearchOutcome {

    private final KnowledgeSourceId sourceId;
    private final List<FoundResource> hits;
    private final String problem;

    private SourceSearchOutcome(KnowledgeSourceId sourceId, List<FoundResource> hits, String problem) {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId must not be null");
        }
        this.sourceId = sourceId;
        this.hits = Collections.unmodifiableList(new ArrayList<FoundResource>(hits));
        this.problem = problem;
    }

    public static SourceSearchOutcome found(KnowledgeSourceId sourceId, List<FoundResource> hits) {
        return new SourceSearchOutcome(sourceId, hits == null ? Collections.<FoundResource>emptyList() : hits, null);
    }

    /** @param problem kurze Meldung ohne Zugangsdaten, z. B. „nicht erreichbar“ */
    public static SourceSearchOutcome failed(KnowledgeSourceId sourceId, String problem) {
        return new SourceSearchOutcome(sourceId, Collections.<FoundResource>emptyList(),
                problem == null || problem.trim().isEmpty() ? "Suche fehlgeschlagen" : problem.trim());
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    public List<FoundResource> hits() {
        return hits;
    }

    /** {@code null}, wenn die Quelle geantwortet hat. */
    public String problem() {
        return problem;
    }

    public boolean failed() {
        return problem != null;
    }
}
