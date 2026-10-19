package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.proasteion.application.agent.BrowserMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.chat.TurnContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.InformationNeed;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    static final String RESEARCH = ResearchDelegationTool.NAME + " nutzt du, wenn der Nutzer etwas über einen "
            + "Begriff wissen will, der im Kontext nicht eindeutig aufgelöst ist und organisationsspezifisch sein könnte "
            + "(Abkürzung, interner System- oder Projektname, Organisationseinheit, Dokument, interner Prozess, "
            + "unbekannter Fachbegriff), und wenn er ausdrücklich ums Suchen oder Nachschlagen bittet. Beschreibe den "
            + "Auftrag in natürlicher Sprache; Suchbegriffe wählt der Recherche-Agent. Dass du irgendeine Bedeutung "
            + "kennst, ersetzt die Recherche nicht. Antworte nur aus den Befunden und nenne ihre Quellen; findet sie "
            + "nichts, sag das und nenne eine öffentliche Bedeutung nur als mögliche Lesart, nie stillschweigend.";

    static final String BROWSER = "Im Browser liest du mehr als eine Seite immer parallel mit "
            + BrowserMcpTools.OPEN_MANY + " (Suchergebnisse, Linklisten, Teaser; per links=[Ids], urls oder "
            + "from_page), nicht nacheinander mit " + BrowserMcpTools.FOLLOW + ". Trefferlisten und Teaser zeigen nur "
            + "Ausschnitte: öffne die relevanten Seiten, bevor du antwortest. " + BrowserMcpTools.FOLLOW
            + " nur für eine einzelne Seite oder wenn du auf der Seite etwas bedienen musst; Captcha-, Anmelde- und "
            + "Einwilligungsseiten bleiben einzeln.";

    static final String ROUTE_PREFIX = "[Routing für diese Nachricht laut Antwortplan] ";

    static final String ROUTE_DIRECT = ROUTE_PREFIX + "Antworte direkt, ohne Programmhilfe und ohne Recherche in den "
            + "Wissensquellen, außer die Nachricht verlangt das ausdrücklich.";

    static final String ROUTE_HELP = ROUTE_PREFIX + "Es geht um dieses Programm: nutze " + AppHelpMcpTools.APP_HELP_SEARCH
            + " und " + AppHelpMcpTools.APP_HELP_READ + ", nicht die Wissensquellen des Nutzers.";

    static final String ROUTE_RESEARCH = ROUTE_PREFIX + "Organisationsspezifischer Informationsbedarf: nutze "
            + ResearchDelegationTool.NAME + " mit dem Auftrag in natürlicher Sprache, bevor du antwortest.";

    static final String ROUTE_SEARCH = ROUTE_PREFIX + "Organisationsspezifischer Informationsbedarf: nutze "
            + KnowledgeMcpTools.KNOWLEDGE_SEARCH + " mit wenigen Kernbegriffen, bevor du antwortest.";

    private ToolUsePolicy() {
    }

    /** What the chat should do for a turn, derived from the answer plan; names no tool. */
    enum Route {
        DIRECT, APPLICATION_HELP, RESEARCH, NONE
    }

    /** The route of {@code plan}: the planned next action decides, else the information need. */
    static Route route(AnswerPlan plan) {
        if (plan == null || !plan.isKnown()) {
            return Route.NONE;
        }
        NextAction next = plan.nextAction();
        if (next == NextAction.USE_APPLICATION_HELP) {
            return Route.APPLICATION_HELP;
        }
        if (next == NextAction.SEARCH_INTERNAL_KNOWLEDGE || next == NextAction.RESOLVE_INTERNAL_TERM) {
            return Route.RESEARCH;
        }
        if (next == NextAction.ANSWER_DIRECTLY) {
            return Route.DIRECT;
        }
        if (next != NextAction.UNKNOWN) {
            return Route.NONE; // clarification is answered by the client, external search is not routed here
        }
        InformationNeed need = plan.informationNeed();
        if (need == InformationNeed.APPLICATION_HELP) {
            return Route.APPLICATION_HELP;
        }
        if (need == InformationNeed.INTERNAL_KNOWLEDGE || need == InformationNeed.TERM_RESOLUTION) {
            return Route.RESEARCH;
        }
        if (need == InformationNeed.NO_INFORMATION_NEEDED || need == InformationNeed.GENERAL_KNOWLEDGE) {
            return Route.DIRECT;
        }
        return Route.NONE;
    }

    /**
     * @param offered names of the tools the turn offers
     * @return the routing rule for a plan, naming only offered tools; a route whose tool is not offered gives no rule
     */
    public static TurnContext.PlanRouting routing(Collection<String> offered) {
        final List<String> tools = offered == null ? new ArrayList<String>() : new ArrayList<String>(offered);
        return plan -> rule(route(plan), tools);
    }

    static String rule(Route route, Collection<String> offered) {
        switch (route) {
            case DIRECT:
                return offered.isEmpty() ? null : ROUTE_DIRECT;
            case APPLICATION_HELP:
                return offered.contains(AppHelpMcpTools.APP_HELP_SEARCH) ? ROUTE_HELP : null;
            case RESEARCH:
                if (offered.contains(ResearchDelegationTool.NAME)) {
                    return ROUTE_RESEARCH;
                }
                return offered.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH) ? ROUTE_SEARCH : null;
            default:
                return null;
        }
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
        if (offered.contains(ResearchDelegationTool.NAME)) {
            text.append(' ').append(RESEARCH);
        } else if (offered.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH)) {
            text.append(' ').append(KNOWLEDGE);
        }
        if (offered.contains(BrowserMcpTools.OPEN_MANY)) {
            text.append(' ').append(BROWSER);
        }
        return text.toString();
    }
}
