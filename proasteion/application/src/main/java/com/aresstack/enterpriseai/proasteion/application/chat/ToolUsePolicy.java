package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;

import java.util.Collection;

/**
 * Client rules for choosing tools in the chat, as system part of every turn with tools. They apply regardless of the
 * configured system prompt (it lives in the user's file and may be older): no tool for conversation, questions about
 * the assistant, corrections and complaints; no search merely because of an unknown word; no variants of the same
 * term after a search without hits; questions about the application go to the built-in help first. Name only what the
 * turn actually offers. Pure and stateless.
 */
public final class ToolUsePolicy {

    static final String GENERAL = "[Werkzeugregeln des Clients] Nutze Werkzeuge nur, wenn die Antwort sie wirklich "
            + "braucht. Begrüßungen, Small Talk, Dank, Fragen nach dir selbst und deinen Fähigkeiten, Rückfragen, "
            + "Korrekturen und Beschwerden beantwortest du direkt, ohne Werkzeug. Rufe kein Suchwerkzeug auf, nur weil "
            + "die Nachricht ein unbekanntes Wort enthält: prüfe zuerst, ob es ein Tipp- oder Erkennungsfehler eines "
            + "gebräuchlichen Wortes ist, mit dem der Satz Sinn ergibt. Bringt eine Suche keine Treffer, suche nicht "
            + "mit Varianten oder Teilen derselben Begriffe weiter; prüfe dein Verständnis der Nachricht oder frag kurz "
            + "nach.";

    static final String HELP_FIRST = "Fragen zu diesem Programm (Bedienung, Funktionen, Einstellungen, Einrichtung, "
            + "Fehlermeldungen, ein Überblick über das Programm) beantwortest du zuerst mit "
            + AppHelpMcpTools.APP_HELP_SEARCH + " und " + AppHelpMcpTools.APP_HELP_READ
            + ", nicht mit den Wissensquellen des Nutzers.";

    static final String KNOWLEDGE = KnowledgeMcpTools.KNOWLEDGE_SEARCH + " ist für fachliches, internes oder "
            + "organisationsspezifisches Wissen aus den Wissensquellen des Nutzers, nicht der Standardweg für jede "
            + "Nachricht.";

    private ToolUsePolicy() {
    }

    /**
     * @param offered names of the tools the turn offers
     * @return the rules for these tools, or {@code null} when the turn offers none
     */
    public static String context(Collection<String> offered) {
        if (offered == null || offered.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder(GENERAL);
        if (offered.contains(AppHelpMcpTools.APP_HELP_SEARCH)) {
            text.append(' ').append(HELP_FIRST);
        }
        if (offered.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH)) {
            text.append(' ').append(KNOWLEDGE);
        }
        return text.toString();
    }
}
