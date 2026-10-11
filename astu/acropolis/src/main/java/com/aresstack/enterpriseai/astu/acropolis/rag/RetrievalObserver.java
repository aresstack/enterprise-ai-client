package com.aresstack.enterpriseai.astu.acropolis.rag;

/**
 * Erfährt, was bei einer Suche des {@link RetrieveKnowledgeUseCase} nicht wie gewollt lief, damit die Composition
 * Root es ins technische Log schreiben kann (der Kern loggt nicht). Gilt für jeden Aufrufer der Suche: Chat-RAG und
 * {@code knowledge_search}. Wird auf dem Thread der Suche gerufen und kehrt schnell zurück.
 */
public interface RetrievalObserver {

    /** Ein Suchpfad ist ausgefallen; {@code warning} nennt Pfad, Fehlerart und Meldung des Ports. */
    default void pathFailed(RetrievalWarning warning) {
    }

    /**
     * Die Anfrage war für das Embedding zu lang und wurde für die semantische Suche gekürzt (die Volltextsuche
     * bekommt sie ungekürzt).
     *
     * @param tokens Länge der Anfrage
     * @param limit  Länge nach dem Kürzen
     */
    default void queryShortened(int tokens, int limit) {
    }

    /** Ein Beobachter, der nichts tut. */
    static RetrievalObserver none() {
        return new RetrievalObserver() {
        };
    }
}
