package com.aresstack.enterpriseai.application.agent;

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

    void onCompleted();

    void onCancelled();

    void onFailed(AgentFailure failure);
}
