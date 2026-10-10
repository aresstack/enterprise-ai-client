package com.aresstack.enterpriseai.application.tool;

/** Meldet, welches Werkzeug die Schleife gerade ausführt (für eine kurze Statuszeile). Kehrt schnell zurück. */
public interface ToolActivityListener {

    /** Das Modell hat {@code toolName} aufgerufen; die Ausführung beginnt jetzt. */
    void onToolCall(String toolName);
}
