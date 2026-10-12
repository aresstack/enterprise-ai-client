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
 * questions about the assistant or about operating the application, follow-ups, corrections and complaints need no
 * search, and the verbatim message is rarely the best query (filler words, references to the history, recognition
 * errors of a dictation).
 *
 * <p>The model decides in an aside request (no tools, no streaming, not in the history), not a word list:
 * {@code {"search": true|false, "query": "..."}}. If the verdict is unreadable or the aside request fails, search with
 * the message itself as before ({@link Decision#isKnown()} is {@code false} then). Stateless.
 */
public final class RetrievalTriage {

    static final String INSTRUCTION = "Du entscheidest für einen Wissens-Chat, ob vor der Antwort in den "
            + "Wissensquellen des Nutzers (z. B. Confluence, Wiki, Dokumentablagen) gesucht werden muss. Antworte "
            + "ausschließlich mit einem JSON-Objekt der Form {\"search\": true, \"query\": \"\"}.\n"
            + "- search = false bei Gruß, Small Talk, Dank, Zustimmung und Abschied; bei Fragen nach dir selbst, deinen "
            + "Fähigkeiten oder Werkzeugen; bei Fragen zur Bedienung, zu Funktionen, Einstellungen oder Fehlern dieses "
            + "Programms (dafür gibt es die eingebaute Programmhilfe); bei Rückfragen, Korrekturen, Beschwerden, "
            + "Beschimpfungen und Äußerungen über das Gespräch selbst; bei Aufgaben, die kein Wissen aus den Quellen "
            + "brauchen (formulieren, übersetzen, rechnen).\n"
            + "- Ein unbekanntes Wort allein ist kein Grund zu suchen. Prüfe zuerst, ob es ein Tipp- oder "
            + "Erkennungsfehler eines gebräuchlichen Wortes ist, mit dem der Satz Sinn ergibt.\n"
            + "- search = true nur, wenn die Antwort fachliches, internes oder organisationsspezifisches Wissen aus den "
            + "Quellen braucht. Dann ist query eine Suchanfrage in wenigen Stichworten: löse Bezüge aus dem Verlauf "
            + "auf, korrigiere offensichtliche Tipp- und Erkennungsfehler und lass Füllwörter weg.";

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
        if (!enabled) {
            return Decision.unknown();
        }
        String verdict;
        try {
            verdict = chat.completeAside(request(history, message, origin), options).content();
        } catch (ChatCompletionException e) {
            return Decision.unknown();
        } catch (RuntimeException e) {
            return Decision.unknown();
        }
        return parse(verdict);
    }

    static List<ChatMessage> request(List<ChatMessage> history, String message, MessageOrigin origin) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        messages.add(ChatMessage.system(INSTRUCTION));
        messages.add(ChatMessage.user(FollowUpResearch.conversation(history, message, origin).toString().trim()));
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
