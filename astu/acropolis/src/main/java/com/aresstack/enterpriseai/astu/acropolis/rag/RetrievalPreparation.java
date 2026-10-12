package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.Collection;

/**
 * Schritt vor dem Retrieval eines RAG-Turns: Die Composition Root lässt hier die angehakten Quellen live durchsuchen
 * und neue oder geänderte Treffer indexieren (Suche füllt den Index, wie MainframeMate), damit das anschließende
 * Retrieval sie schon findet. Fehler bleiben im Schritt; das Retrieval läuft in jedem Fall.
 *
 * <p>Gestuft ({@link #begin}): Der adaptive Modus des {@link RagChatUseCase} wartet zuerst nur auf wenige geladene
 * Treffer und erst dann auf weitere ({@link Stage#awaitMore()}), wenn die Evidenz nicht reicht. So endet eine
 * Faktfrage, sobald die erste passende Seite im Index ist.
 */
public interface RetrievalPreparation {

    /** @param sources die freigegebenen Quellen des Turns; leer = alle */
    void prepare(String query, Collection<SourceAddress> sources);

    /**
     * Wie {@link #prepare}, kehrt aber womöglich schon zurück, bevor alle Treffer indexiert sind. Standard: wartet
     * wie {@link #prepare} und hat danach nichts mehr nachzuladen.
     */
    default Stage begin(String query, Collection<SourceAddress> sources) {
        prepare(query, sources);
        return Stage.done();
    }

    /** Eine begonnene Vorbereitung, deren übrige Treffer noch in den Index kommen können. */
    interface Stage {

        /** Nichts mehr nachzuladen. */
        static Stage done() {
            return () -> false;
        }

        /**
         * Wartet, bis weitere Treffer indexiert sind (blockiert).
         *
         * @return {@code false}, wenn es nichts mehr nachzuladen gab; dann lohnt keine weitere Suche im Index
         */
        boolean awaitMore();
    }

    static RetrievalPreparation none() {
        return (query, sources) -> {
        };
    }
}
