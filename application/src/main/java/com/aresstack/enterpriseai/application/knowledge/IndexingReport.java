package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ergebnis eines Indexierungslaufs über eine Quelle. */
public final class IndexingReport {

    private final KnowledgeSourceId sourceId;
    private final int discovered;
    private final List<ResourceIndexingOutcome> outcomes;
    private final boolean cancelled;
    private final String discoveryFailure;
    private final String pruneFailure;

    IndexingReport(KnowledgeSourceId sourceId, int discovered, List<ResourceIndexingOutcome> outcomes,
                   boolean cancelled, String discoveryFailure) {
        this(sourceId, discovered, outcomes, cancelled, discoveryFailure, null);
    }

    IndexingReport(KnowledgeSourceId sourceId, int discovered, List<ResourceIndexingOutcome> outcomes,
                   boolean cancelled, String discoveryFailure, String pruneFailure) {
        this.sourceId = sourceId;
        this.discovered = discovered;
        this.outcomes = Collections.unmodifiableList(new ArrayList<ResourceIndexingOutcome>(outcomes));
        this.cancelled = cancelled;
        this.discoveryFailure = discoveryFailure;
        this.pruneFailure = pruneFailure;
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    /** Anzahl der in der Discovery gefundenen Ressourcen. */
    public int discovered() {
        return discovered;
    }

    /**
     * Je verarbeitete Ressource ein Eintrag, in Verarbeitungsreihenfolge; am Ende die aus dem Index entfernten
     * ({@link IndexingStatus#PRUNED}), weil die Discovery sie nicht mehr geliefert hat.
     */
    public List<ResourceIndexingOutcome> outcomes() {
        return outcomes;
    }

    public int count(IndexingStatus status) {
        int n = 0;
        for (ResourceIndexingOutcome outcome : outcomes) {
            if (outcome.status() == status) {
                n++;
            }
        }
        return n;
    }

    /** Summe der geschriebenen Chunks. */
    public int chunkCount() {
        int n = 0;
        for (ResourceIndexingOutcome outcome : outcomes) {
            n += outcome.chunkCount();
        }
        return n;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Die Discovery selbst ist gescheitert; dann wurde nichts verarbeitet. */
    public boolean discoveryFailed() {
        return discoveryFailure != null;
    }

    /** Meldung zur gescheiterten Discovery oder leer. */
    public String discoveryFailure() {
        return discoveryFailure == null ? "" : discoveryFailure;
    }

    /**
     * Die Abfrage der indexierten Ressourcen am Ende des Laufs ist gescheitert; verschwundene Ressourcen wurden
     * nicht entfernt, alles andere gilt.
     */
    public boolean pruneFailed() {
        return pruneFailure != null;
    }

    /** Meldung zur gescheiterten Bereinigung oder leer. */
    public String pruneFailure() {
        return pruneFailure == null ? "" : pruneFailure;
    }

    /** Lauf vollständig: nicht abgebrochen, Discovery und Bereinigung gelungen, keine gescheiterte Ressource. */
    public boolean isComplete() {
        return !cancelled && !discoveryFailed() && !pruneFailed() && count(IndexingStatus.FAILED) == 0;
    }

    @Override
    public String toString() {
        return "IndexingReport{" + sourceId + ", discovered=" + discovered + ", indexed="
                + count(IndexingStatus.INDEXED) + ", unchanged=" + count(IndexingStatus.UNCHANGED) + ", pruned="
                + count(IndexingStatus.PRUNED) + ", failed=" + count(IndexingStatus.FAILED) + ", chunks="
                + chunkCount() + (cancelled ? ", cancelled" : "")
                + (discoveryFailed() ? ", discoveryFailure=" + discoveryFailure : "")
                + (pruneFailed() ? ", pruneFailure=" + pruneFailure : "") + "}";
    }
}
