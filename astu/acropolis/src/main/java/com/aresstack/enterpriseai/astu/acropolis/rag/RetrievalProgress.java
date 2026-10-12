package com.aresstack.enterpriseai.astu.acropolis.rag;

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

    /** Ein Suchpfad im Index beginnt (Volltext oder semantisch). */
    default void pathStarted(RetrievalPath path) {
    }

    /** Die Suche hat {@code hits} Treffer geliefert (nach Fusion und Kürzung). */
    default void hitsFound(String query, int hits) {
    }

    /** Alle Suchpfade dieser Suche sind ausgefallen. */
    default void searchFailed(String query) {
    }

    /** Das Modell prüft, ob die bisherigen Treffer die Nachricht beantworten. */
    default void contextCheckStarted() {
    }

    /** Die Suche ist beendet; der Kontext des Turns hat {@code sources} Quellen. */
    default void contextReady(int sources) {
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
