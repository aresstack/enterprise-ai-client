package com.aresstack.enterpriseai.astu.acropolis.tool;

import java.util.List;

/** Meldet, welches Werkzeug die Schleife gerade ausführt (für eine kurze Statuszeile). Kehrt schnell zurück. */
public interface ToolActivityListener {

    /** Das Modell hat {@code toolName} aufgerufen; die Ausführung beginnt jetzt. */
    void onToolCall(String toolName);

    /**
     * Wie {@link #onToolCall(String)}, mit den Argumenten des Aufrufs (JSON, wie vom Modell), damit eine Anzeige den
     * Suchbegriff oder die gelesene Seite nennen kann, bevor das Werkzeug läuft. Standard: {@link #onToolCall(String)}.
     */
    default void onToolCall(String toolName, String arguments) {
        onToolCall(toolName);
    }

    /**
     * {@code toolName} ist fertig: {@code succeeded} ist {@code false}, wenn das Werkzeug geworfen oder ein
     * {@value ToolRegistry#ERROR_PREFIX}-Ergebnis geliefert hat. So weiß der Aufrufer, ob ein Werkzeug dem Turn
     * tatsächlich etwas geliefert hat (z. B. Wissenskontext über {@code knowledge_read}).
     */
    default void onToolResult(String toolName, boolean succeeded) {
    }

    /**
     * {@code toolName} succeeded and handed the content of {@code resources} to the model; comes before
     * {@link #onToolResult} and only when there is at least one resource. Record these as provenance of the answer.
     */
    default void onToolContext(String toolName, List<ToolContextResource> resources) {
    }

    /**
     * Wie {@link #onToolResult}, mit Argumenten und Ergebnis des Aufrufs, für die Diagnose (gespeicherter
     * Werkzeugverlauf einer Antwort). Kommt nach {@link #onToolResult}.
     */
    default void onToolExecuted(String toolName, String arguments, String output, boolean succeeded) {
    }
}
