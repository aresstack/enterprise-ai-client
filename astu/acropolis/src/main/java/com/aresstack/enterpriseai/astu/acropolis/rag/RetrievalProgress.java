package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;

/**
 * Fortschritt der Wissenssuche eines RAG-Turns, während sie läuft ({@link RagChatUseCase}): welche Suche gerade läuft
 * (die Nachricht selbst, danach die Nachsuchen der {@link FollowUpResearch}), welcher Schritt (Live-Suche, Volltext,
 * semantisch, Kontextprüfung) und wie viele Treffer sie brachte. So sieht die Oberfläche, warum ein Turn dauert,
 * statt erst mit dem fertigen {@link RagChatTurn} davon zu erfahren.
 *
 * <p>Gemeldet wird nur Beobachtbares: das Urteil der Kontextprüfung kommt als „reicht“ ({@link #contextReady}) oder als
 * nächste Suche ({@link #searchStarted} mit höherer Nummer), nie als Begründung des Modells. Wird auf dem Thread der
 * Suche gerufen und kehrt schnell zurück; alle Methoden haben leere Standardfassungen.
 */
public interface RetrievalProgress {

    /**
     * Eine Suche beginnt.
     *
     * @param number 1 für die Suche mit der Nachricht, danach die Nachsuchen
     * @param max    höchstens so viele Suchen hat dieser Turn (1 + erlaubte Nachsuchen)
     */
    default void searchStarted(String query, int number, int max) {
    }

    /** Die angehakten Quellen werden live durchsucht, neue Treffer kommen in den Index ({@link RetrievalPreparation}). */
    default void liveSearchStarted(String query) {
    }

    /** Die Live-Suche hat in den Quellen {@code hits} Treffer gefunden; sie werden jetzt geladen. */
    default void liveHitsFound(int hits) {
    }

    /**
     * {@code available} der {@code awaited} Treffer, auf die die Suche wartet, sind geladen und lokal durchsuchbar
     * (Rohindex, ohne Passagen und Embeddings). Kommt mit {@code available = 0}, wenn das Warten beginnt.
     */
    default void pagesAvailable(int available, int awaited) {
    }

    /** Ein Suchpfad im Index beginnt (Volltext oder semantisch). */
    default void pathStarted(RetrievalPath path) {
    }

    /** Die Suche hat {@code hits} Treffer geliefert (nach Fusion und Kürzung). */
    default void hitsFound(String query, int hits) {
    }

    /** Alle Suchpfade dieser Suche sind ausgefallen. */
    default void searchFailed(String query) {
    }

    /** Das Modell bewertet {@code candidates} Treffer gegen die Nachricht (adaptiver Modus). */
    default void rankingStarted(int candidates) {
    }

    /** Die Treffer sind bewertet: {@code relevant} von {@code candidates} tragen zur Antwort bei. */
    default void candidatesRanked(int candidates, int relevant) {
    }

    /**
     * Das Urteil der Bewertung: mit {@code sources} Abschnitten aus {@code independent} verschiedenen Seiten reicht die
     * Evidenz ({@code sufficient}) oder noch nicht (adaptiver Modus).
     */
    default void evidenceChecked(int sources, int independent, boolean sufficient) {
    }

    /** Es wird auf weitere Treffer der Live-Suche gewartet, weil die Evidenz noch nicht reicht. */
    default void morePagesStarted() {
    }

    /** Das Modell prüft, ob die bisherigen Treffer die Nachricht beantworten. */
    default void contextCheckStarted() {
    }

    /** Die Suche ist beendet; der Kontext des Turns hat {@code sources} Quellen. */
    default void contextReady(int sources) {
    }

    /** Der Wissenskontext des vorigen Turns ({@code sources} Auszüge) beantwortet die Nachricht; es wird nicht gesucht. */
    default void reusingSources(int sources) {
    }

    /**
     * Der Wissenskontext des vorigen Turns reicht nicht; es folgt eine gezielte Nachsuche mit {@code query}.
     *
     * @param sources die Auszüge des vorigen Turns, die (zum Thema passend) im Kontext bleiben; 0 bei Themenwechsel
     */
    default void reuseInsufficient(String query, int sources) {
    }

    /** The model plans the answer before anything else happens in this turn ({@code AnswerPlanningService}). */
    default void planningStarted() {
    }

    /**
     * The plan is ready.
     *
     * @param next the planned next step; {@code UNKNOWN} when no plan was obtained
     */
    default void planningFinished(NextAction next) {
    }

    /** Das Modell prüft vor der ersten Suche, ob die Nachricht Wissen aus den Quellen braucht ({@link RetrievalTriage}). */
    default void triageStarted() {
    }

    /** Die Nachricht braucht kein Wissen aus den Quellen; es wird nicht gesucht, der Turn startet ohne Kontext. */
    default void searchSkipped() {
    }

    /** {@code true}, wenn der Nutzer abgebrochen hat: weitere Suchschritte entfallen. */
    default boolean isCancelled() {
        return false;
    }

    /** Ein Fortschritt, der nichts meldet und nie abbricht. */
    static RetrievalProgress none() {
        return new RetrievalProgress() {
        };
    }
}
