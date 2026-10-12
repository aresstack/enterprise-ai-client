package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decide before the first search of a RAG turn whether the message needs knowledge from the checked sources at all,
 * and with which query to search. Checked sources mean "available", not "search on every message": greetings,
 * questions about the assistant or about operating the application, corrections and complaints need no search. Terms
 * that may carry an organization-specific meaning (acronyms, identifiers, system or project names) are searched even
 * when the model knows a public meaning. The verbatim message is rarely the best query (filler words, references to
 * the history, recognition errors of a dictation).
 *
 * <p>The model decides in an aside request (no tools, no streaming, not in the history), not a word list:
 * {@code {"search": true|false, "query": "..."}}. If the verdict is unreadable or the aside request fails, search with
 * the message itself as before ({@link Decision#isKnown()} is {@code false} then). Stateless.
 */
public final class RetrievalTriage {

    /**
     * The principles shared by the triage and the check of earlier context: the information need decides, not whether a
     * word is unknown or known. A term the user asks about is looked up when it is not resolved in the context and may
     * be organization-specific, even if a public meaning is known; the client's analysis block is a hint only.
     */
    static final String ROUTING = "Nutze den Block <client-turn-analysis>, falls vorhanden, als Hinweis zur Form der "
            + "Nachricht, nicht als Wahrheit. Entscheide nach der aktuellen Äußerung, dem Verlauf und dem "
            + "Informationsbedarf:\n"
            + "- Direkte Antwort (search = false), wenn kein Wissen aus den Quellen gebraucht wird: Gruß, Small Talk, "
            + "Dank, Zustimmung, Korrektur, Beschwerde; Aussagen und Meinungen, auch mit unbekanntem Begriff; Fragen an "
            + "dich selbst; Aufgaben wie formulieren oder übersetzen; allgemein eindeutige Begriffe und "
            + "Allgemeinwissen.\n"
            + "- Anwendungshilfe (search = false): Fragen zur Bedienung, Oberfläche, zu Einstellungen oder Funktionen "
            + "dieses Programms; dafür gibt es die eingebaute Programmhilfe.\n"
            + "- Organisationswissen (search = true): Der Nutzer will etwas über einen Begriff wissen, der im aktuellen "
            + "Kontext nicht eindeutig aufgelöst ist und organisationsspezifisch sein könnte (Abkürzung, interner "
            + "System- oder Projektname, Organisationseinheit, Dokumentbezeichnung, interner Prozess, unbekannter "
            + "Fachbegriff). Entscheidend ist nicht, ob du irgendeine Bedeutung kennst, sondern ob der Begriff hier "
            + "eindeutig aufgelöst ist.\n"
            + "- Ausdrücklicher Suchauftrag (search = true): Der Nutzer bittet darum, etwas zu suchen, nachzuschlagen "
            + "oder zu recherchieren.\n"
            + "- Spracheingabe: Meldet die Analyse bei einer diktierten Nachricht einen möglichen Erkennungsfehler, "
            + "prüfe zuerst eine plausible Fehlerkennung und suche nicht nach dem verdächtigen Wort.\n"
            + "- Bezüge auf den Verlauf (es, das, dort) löse aus dem Verlauf auf.";

    static final String INSTRUCTION = "Du entscheidest für einen Wissens-Chat, ob vor der Antwort in den "
            + "Wissensquellen des Nutzers (z. B. Confluence, Wiki, Dokumentablagen) gesucht werden muss. Antworte "
            + "ausschließlich mit einem JSON-Objekt der Form {\"search\": true, \"query\": \"\"}.\n"
            + ROUTING + "\n"
            + "Bei search = true ist query eine Suchanfrage in wenigen Stichworten: nimm den gesuchten Begriff "
            + "unverändert auf, löse Bezüge aus dem Verlauf auf, korrigiere offensichtliche Tipp- und Erkennungsfehler "
            + "und lass Füllwörter weg.";

    private static final Pattern SEARCH = Pattern.compile("\"search\"\\s*:\\s*(true|false)");
    private static final Pattern QUERY = Pattern.compile("\"query\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final boolean enabled;

    private RetrievalTriage(boolean enabled) {
        this.enabled = enabled;
    }

    /** Skip the triage: search with the message itself (previous flow). */
    public static RetrievalTriage off() {
        return new RetrievalTriage(false);
    }

    /** Let the model decide per message whether and with what to search. */
    public static RetrievalTriage byModel() {
        return new RetrievalTriage(true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Whether and with what to search. */
    static final class Decision {

        private final boolean known;
        private final boolean search;
        private final String query;

        private Decision(boolean known, boolean search, String query) {
            this.known = known;
            this.search = search;
            this.query = query;
        }

        /** No readable verdict: search with the message, as without triage. */
        static Decision unknown() {
            return new Decision(false, true, null);
        }

        boolean isKnown() {
            return known;
        }

        /** {@code false}: the message needs no knowledge from the sources; neither search nor context. */
        boolean isSearch() {
            return search;
        }

        /** The query, or {@code null} to search with the message itself. */
        String query() {
            return query;
        }
    }

    /**
     * @param history the history before the current message
     * @param origin  how the message came about (dictated or typed)
     */
    Decision decide(ChatService chat, List<ChatMessage> history, String message, MessageOrigin origin,
                    ChatOptions options) {
        return decide(chat, history, message, origin, null, options);
    }

    /** As above, with the client's request analysis as a hint for the model, or {@code null}. */
    Decision decide(ChatService chat, List<ChatMessage> history, String message, MessageOrigin origin,
                    String analysis, ChatOptions options) {
        if (!enabled) {
            return Decision.unknown();
        }
        String verdict;
        try {
            verdict = chat.completeAside(request(history, message, origin, analysis), options).content();
        } catch (ChatCompletionException e) {
            return Decision.unknown();
        } catch (RuntimeException e) {
            return Decision.unknown();
        }
        return parse(verdict);
    }

    static List<ChatMessage> request(List<ChatMessage> history, String message, MessageOrigin origin) {
        return request(history, message, origin, null);
    }

    static List<ChatMessage> request(List<ChatMessage> history, String message, MessageOrigin origin,
                                     String analysis) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        messages.add(ChatMessage.system(INSTRUCTION));
        messages.add(ChatMessage.user(FollowUpResearch.conversation(history, message, origin, analysis).toString()
                .trim()));
        return messages;
    }

    static Decision parse(String verdict) {
        if (verdict == null) {
            return Decision.unknown();
        }
        Matcher search = SEARCH.matcher(verdict);
        if (!search.find()) {
            return Decision.unknown();
        }
        if (!Boolean.parseBoolean(search.group(1))) {
            return new Decision(true, false, null);
        }
        Matcher query = QUERY.matcher(verdict);
        String value = query.find()
                ? FollowUpResearch.unescape(query.group(1)).replace('\r', ' ').replace('\n', ' ').trim() : "";
        return new Decision(true, true, value.isEmpty() ? null : value);
    }
}
