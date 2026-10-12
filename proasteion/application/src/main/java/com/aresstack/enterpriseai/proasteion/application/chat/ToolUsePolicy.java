package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;

import java.util.Collection;

/**
 * Client rules for choosing tools in the chat, as system part of every turn with tools. They apply regardless of the
 * configured system prompt (it lives in the user's file and may be older): no tool for conversation, questions about
 * the assistant, corrections and complaints; a likely typo or recognition error is read, not searched; no variants of
 * the same term after a search without hits; questions about the application go to the built-in help first; terms
 * that may have an organization-specific meaning go to the knowledge sources before any public meaning. Name only what the
 * turn actually offers. Pure and stateless.
 */
public final class ToolUsePolicy {

    static final String GENERAL = "[Werkzeugregeln des Clients] Nutze Werkzeuge nur, wenn die Antwort sie wirklich "
            + "braucht. Begrüßungen, Small Talk, Dank, Fragen nach dir selbst und deinen Fähigkeiten, Korrekturen und "
            + "Beschwerden beantwortest du direkt, ohne Werkzeug. Ergibt ein Wort mit einem ähnlich klingenden "
            + "gebräuchlichen Wort einen deutlich plausibleren Satz, ist es eher ein Tipp- oder Erkennungsfehler: nimm "
            + "diese Lesart und suche nicht danach. Bringt eine Suche keine Treffer, suche nicht mit Varianten oder "
            + "Teilen derselben Begriffe weiter; prüfe dein Verständnis der Nachricht oder frag kurz nach.";

    static final String HELP_FIRST = "Fragen zu diesem Programm (Bedienung, Funktionen, Einstellungen, Einrichtung, "
            + "Fehlermeldungen, ein Überblick über das Programm) beantwortest du zuerst mit "
            + AppHelpMcpTools.APP_HELP_SEARCH + " und " + AppHelpMcpTools.APP_HELP_READ
            + ", nicht mit den Wissensquellen des Nutzers.";

    static final String KNOWLEDGE = KnowledgeMcpTools.KNOWLEDGE_SEARCH + " ist für Wissen, das organisationsspezifisch "
            + "sein kann, nicht der Standardweg für jede Nachricht. Kann ein zentraler Begriff der Frage im "
            + "Arbeitskontext eine interne Bedeutung haben (Abkürzung, Akronym, Kennung, System-, Projekt-, Prozess- "
            + "oder Organisationsname, Dokumenttitel, unerklärter Fachbegriff), suche zuerst dort, auch wenn du eine "
            + "öffentliche Bedeutung kennst, und ebenso, wenn der Nutzer ausdrücklich ums Nachschlagen bittet. Eine "
            + "interne Bedeutung hat Vorrang. Findet die Suche nichts, sag das und nenne eine öffentliche Bedeutung nur "
            + "als mögliche Lesart, nie stillschweigend.";

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
