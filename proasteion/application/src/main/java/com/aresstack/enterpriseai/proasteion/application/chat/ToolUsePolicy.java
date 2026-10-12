package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;

import java.util.Collection;

/**
 * Client principles for choosing tools in the chat, as system part of every turn with tools. They apply regardless of
 * the configured system prompt (it lives in the user's file and may be older): the information need decides, the
 * client's analysis is a hint, questions about the application go to the built-in help, terms that are not resolved
 * and may be organization-specific go to the knowledge sources. Name only what the turn actually offers. Name only what the
 * turn actually offers. Pure and stateless.
 */
public final class ToolUsePolicy {

    static final String GENERAL = "[Werkzeugregeln des Clients] Nutze den Antwortplan "
            + "<client-answer-plan> bzw. die Analyse <client-turn-analysis> als Hinweis, nicht als Wahrheit. "
            + "Entscheide anhand der aktuellen Äußerung, des Verlaufs und der verfügbaren Werkzeuge, ob zusätzliche "
            + "Informationen nötig sind. Kein Werkzeug nur deshalb, weil ein Begriff unbekannt ist: "
            + "Gruß, Dank, Korrektur, Beschwerde, Aussagen und Meinungen, Fragen an dich selbst und Allgemeinwissen "
            + "beantwortest du direkt. Ist eine diktierte Nachricht auffällig, prüfe zuerst eine plausible "
            + "Fehlerkennung, bevor du ein verdächtiges Wort als Namen behandelst oder danach suchst. Bringt eine Suche "
            + "keine Treffer, suche nicht mit Varianten derselben Begriffe weiter, sondern prüfe dein Verständnis oder "
            + "frag nach.";

    static final String HELP_FIRST = "Fragen zu diesem Programm (Bedienung, Oberfläche, Einstellungen, Funktionen, "
            + "Einrichtung, Fehlermeldungen, ein Überblick) beantwortest du mit " + AppHelpMcpTools.APP_HELP_SEARCH
            + " und " + AppHelpMcpTools.APP_HELP_READ + ", nicht mit den Wissensquellen des Nutzers.";

    static final String KNOWLEDGE = KnowledgeMcpTools.KNOWLEDGE_SEARCH + " nutzt du, wenn der Nutzer etwas über einen "
            + "Begriff wissen will, der im Kontext nicht eindeutig aufgelöst ist und organisationsspezifisch sein könnte "
            + "(Abkürzung, interner System- oder Projektname, Organisationseinheit, Dokument, interner Prozess, "
            + "unbekannter Fachbegriff), und wenn er ausdrücklich ums Suchen oder Nachschlagen bittet. Dass du "
            + "irgendeine Bedeutung kennst, ersetzt die Suche nicht. Findet sie nichts, sag das und nenne eine "
            + "öffentliche Bedeutung nur als mögliche Lesart, nie stillschweigend.";

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
