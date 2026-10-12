package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.List;

/**
 * Der Fähigkeits-Kontext eines Turns: welche Werkzeuge dem Modell gerade angeboten werden, als System-Anteil für eine
 * Frage nach dem Assistenten selbst („Welche Tools hast du?“). Erzeugt wird er aus genau den Definitionen, die in
 * diesem Turn an das Modell gehen; abgeschaltete oder anderen Kanälen vorbehaltene Werkzeuge kommen darin nicht vor,
 * ein eigenes Werkzeugverzeichnis gibt es nicht. Rein und zustandslos.
 */
public final class ToolCapabilities {

    private ToolCapabilities() {
    }

    /**
     * @param offered die in diesem Turn angebotenen Werkzeuge; {@code null} oder leer: keine
     * @param model   das für diesen Turn gewählte Chat-Modell oder {@code null} (Standard der Einstellungen)
     * @return System-Anteil mit den Werkzeugen und der Anweisung, Fragen nach den eigenen Fähigkeiten nur daraus zu
     *         beantworten
     */
    public static String context(List<ToolDefinition> offered, String model) {
        StringBuilder text = new StringBuilder();
        text.append("Die Nachricht fragt nach dir selbst (Werkzeuge, Fähigkeiten, Modell). Beantworte sie nur aus "
                + "dieser Laufzeitangabe, nicht aus Inhalten der Wissensquellen, und suche dafür nicht in den "
                + "Wissensquellen. Nenne keine Werkzeuge, die hier nicht stehen.\n");
        if (model != null && !model.trim().isEmpty()) {
            text.append("Chat-Modell in diesem Turn: ").append(model.trim()).append('\n');
        }
        if (offered == null || offered.isEmpty()) {
            text.append("In diesem Turn stehen dir keine Werkzeuge zur Verfügung.");
            return text.toString();
        }
        text.append("In diesem Turn verfügbare Werkzeuge:");
        for (ToolDefinition tool : offered) {
            text.append("\n- ").append(tool.name());
            String description = tool.description() == null ? "" : tool.description().trim();
            if (!description.isEmpty()) {
                text.append(" — ").append(firstLine(description));
            }
        }
        return text.toString();
    }

    private static String firstLine(String description) {
        int end = description.indexOf('\n');
        return end < 0 ? description : description.substring(0, end).trim();
    }
}
