package com.aresstack.enterpriseai.astu.acropolis.agent;

/**
 * Beobachtet einen Agent-Auftrag. Nach beliebig vielen {@link #onMessage}/{@link #onThought} folgt genau ein
 * Abschluss: {@link #onCompleted}, {@link #onCancelled} oder {@link #onFailed}; danach kommt nichts mehr.
 *
 * <p>Aufrufe kommen vom Callback-Thread des ACP-Adapters, vom Start-Executor des Service oder synchron vom
 * aufrufenden Thread, nie gleichzeitig. Synchron heißt: ein Abbruch, bevor der Auftrag den Agenten erreicht hat,
 * meldet {@link #onCancelled} auf dem Thread, der {@code cancel()} ruft, und ein abgelehnter Start meldet
 * {@link #onFailed} noch in {@code send()}; das kann der UI-Thread sein. Das Transkript des {@link AgentService} ist beim
 * Abschluss bereits aktualisiert. Implementierungen kehren schnell zurück; UI-Code wechselt selbst auf seinen
 * Event-Thread.
 */
public interface AgentTurnListener {

    /** Ein Stück der Agentenantwort. */
    void onMessage(String text);

    /** Ein Stück der Überlegungen des Agenten (nicht Teil der Antwort). */
    void onThought(String text);

    /**
     * Ein Werkzeugaufruf des Agenten hat begonnen, sich geändert oder ist beendet (askai arch: gelbe Tool-Blasen,
     * grüne Denkblasen). Auch Pläne ({@link AgentToolActivity#KIND_PLAN}, feste Kennung je Auftrag) und sonstige
     * ACP-Ereignisse ({@link AgentToolActivity#KIND_EVENT}) kommen hier an; dieselbe Kennung aktualisiert dieselbe
     * Aktivität. Standard: ignorieren.
     */
    default void onToolActivity(AgentToolActivity activity) {
    }

    /**
     * Die nächsten {@link #onMessage}-Stücke gehören zu einer neuen Äußerung des Agenten (eigene Blase statt
     * Anhängen an die vorige). Kommt nie vor der ersten Äußerung eines Auftrags. Standard: ignorieren.
     */
    default void onUtteranceStarted() {
    }

    void onCompleted();

    void onCancelled();

    void onFailed(AgentFailure failure);
}
