package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;

import java.util.List;

/**
 * Fortschritt und Abbruch eines Indexierungslaufs. Aufrufe kommen auf dem Thread, der den Lauf ausführt;
 * Implementierungen kehren schnell zurück und werfen nicht. Alle Methoden haben leere Defaults.
 */
public interface IndexingListener {

    /** Ein Listener, der nichts tut und nie abbricht. */
    static IndexingListener none() {
        return new IndexingListener() {
        };
    }

    /** Die Discovery ist abgeschlossen; {@code resources} werden nun in dieser Reihenfolge verarbeitet. */
    default void onDiscovered(List<KnowledgeResource> resources) {
    }

    /** Eine Ressource ist fertig (auch gescheitert). */
    default void onResource(ResourceIndexingOutcome outcome) {
    }

    /**
     * Wird vor jeder Ressource gefragt; {@code true} beendet den Lauf nach der aktuellen Ressource. Bereits
     * indexierte Ressourcen bleiben im Index.
     */
    default boolean isCancelled() {
        return false;
    }
}
