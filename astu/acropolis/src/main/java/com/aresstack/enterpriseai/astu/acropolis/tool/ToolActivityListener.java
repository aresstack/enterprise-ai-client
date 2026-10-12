package com.aresstack.enterpriseai.astu.acropolis.tool;

/** Meldet, welches Werkzeug die Schleife gerade ausführt (für eine kurze Statuszeile). Kehrt schnell zurück. */
public interface ToolActivityListener {

    /** Das Modell hat {@code toolName} aufgerufen; die Ausführung beginnt jetzt. */
    void onToolCall(String toolName);

    /**
     * {@code toolName} ist fertig: {@code succeeded} ist {@code false}, wenn das Werkzeug geworfen oder ein
     * {@value ToolRegistry#ERROR_PREFIX}-Ergebnis geliefert hat. So weiß der Aufrufer, ob ein Werkzeug dem Turn
     * tatsächlich etwas geliefert hat (z. B. Wissenskontext über {@code knowledge_read}).
     */
    default void onToolResult(String toolName, boolean succeeded) {
    }

    /**
     * Wie {@link #onToolResult}, mit Argumenten und Ergebnis des Aufrufs, für die Diagnose (gespeicherter
     * Werkzeugverlauf einer Antwort). Kommt nach {@link #onToolResult}.
     */
    default void onToolExecuted(String toolName, String arguments, String output, boolean succeeded) {
    }
}
