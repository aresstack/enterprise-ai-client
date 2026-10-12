package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.List;

/**
 * Die Antwort auf eine Frage nach dem Assistenten selbst („Welche Tools hast du?“, „Welches Modell verwendest du?“),
 * geschrieben vom Client aus dem Laufzeitzustand des Turns: genau die Werkzeuge, die in diesem Turn an das Modell
 * gingen, und das im Composer gewählte Chat-Modell. Das Modell formuliert diese Antwort nicht; so kann sein
 * Selbstbild (Modellname, Wissensstand) nicht hineinrutschen. Abgeschaltete oder anderen Kanälen vorbehaltene
 * Werkzeuge kommen nicht vor, ein eigenes Werkzeugverzeichnis gibt es nicht. Rein und zustandslos.
 */
public final class ToolCapabilities {

    private ToolCapabilities() {
    }

    /**
     * @param offered die in diesem Turn angebotenen Werkzeuge; {@code null} oder leer: keine
     * @param model   das im Composer gewählte Chat-Modell oder {@code null} (dann gilt das der Einstellungen, dessen
     *                Name hier nicht bekannt ist)
     * @return die Antwort als Markdown
     */
    public static String answer(List<ToolDefinition> offered, String model) {
        StringBuilder text = new StringBuilder();
        if (offered == null || offered.isEmpty()) {
            text.append("In diesem Chat stehen mir gerade keine Werkzeuge zur Verfügung. Ist im Seitenmenü eine "
                    + "Wissensquelle angehakt, kann ich darin suchen und Seiten lesen.");
        } else {
            text.append("In diesem Chat stehen mir gerade diese Werkzeuge zur Verfügung:\n");
            for (ToolDefinition tool : offered) {
                text.append("\n- **").append(tool.name()).append("**");
                String description = tool.description() == null ? "" : firstLine(tool.description().trim());
                if (!description.isEmpty()) {
                    text.append(": ").append(description);
                }
            }
        }
        text.append("\n\n");
        if (model != null && !model.trim().isEmpty()) {
            text.append("Es antwortet das im Composer gewählte Chat-Modell **").append(model.trim()).append("**.");
        } else {
            text.append("Welches Chat-Modell antwortet, legen die Einstellungen fest; im Composer ist keines gewählt.");
        }
        return text.toString();
    }

    private static String firstLine(String description) {
        int end = description.indexOf('\n');
        return end < 0 ? description : description.substring(0, end).trim();
    }
}
