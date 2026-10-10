package com.aresstack.enterpriseai.application.tool;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;

/**
 * Ein Werkzeug, das das Modell aufrufen darf: die Definition (Name, Beschreibung, JSON-Schema) für das Modell
 * und die Ausführung im Client. Das Ergebnis geht als Text an das Modell zurück, nie in den sichtbaren Verlauf.
 */
public interface AiTool {

    String name();

    ToolDefinition definition();

    /**
     * @param arguments Argumente als JSON-Objekttext, so wie das Modell sie geschickt hat
     * @return Ergebnistext für das Modell
     * @throws RuntimeException bei ungültigen Argumenten oder Fehlern; die Schleife meldet die Meldung dem Modell
     */
    String execute(String arguments);
}
