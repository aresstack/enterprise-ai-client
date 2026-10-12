package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nachrecherche im normalen Chat: Nach jeder Suche prüft das Modell in einer Nebenanfrage (ohne Werkzeuge, ohne
 * Streaming, nicht in der Historie), ob der gefundene Kontext die aktuelle Nachricht samt Verlauf beantwortet. Falls
 * nicht, nennt es eine genauere Suchanfrage, die Bezüge aus dem Verlauf auflöst („sie“ → der Begriff der Vorfrage);
 * der {@link RagChatUseCase} sucht damit erneut, höchstens {@link #maxFollowUps()} Mal. Erst danach antwortet das
 * Modell. So muss niemand „such noch einmal“ sagen, und eine Rückfrage ohne eigenen Suchbegriff („Welches Format haben
 * sie genau?“) sucht trotzdem nach dem Richtigen.
 *
 * <p>Das Urteil kommt als JSON {@code {"sufficient": true|false, "query": "..."}}. Ist es unlesbar, schlägt die
 * Nebenanfrage fehl oder wiederholt die Anfrage eine schon gestellte, endet die Nachrecherche (kein Endlosschleifen-
 * Risiko); die Antwort entsteht dann aus dem bisherigen Kontext. Zustandslos.
 */
public final class FollowUpResearch {

    /** Höchstzahl der Nachsuchen je Nachricht (kippbar). */
    public static final int DEFAULT_MAX_FOLLOW_UPS = 2;
    /** So viele der letzten Nachrichten des Verlaufs sieht die Prüfung. */
    static final int HISTORY_MESSAGES = 8;
    /** Länger wird eine Nachricht des Verlaufs für die Prüfung nicht. */
    static final int MAX_MESSAGE_CHARS = 2000;

    static final String INSTRUCTION = "Du prüfst für einen Wissens-Chat, ob die gefundenen Abschnitte die aktuelle "
            + "Nachricht des Nutzers beantworten. Antworte ausschließlich mit einem JSON-Objekt der Form "
            + "{\"sufficient\": true, \"query\": \"\"}.\n"
            + "- sufficient = true, wenn der Kontext die Nachricht beantwortet oder die Nachricht kein Wissen aus "
            + "den Wissensquellen braucht.\n"
            + "- sufficient = false, wenn Wissen aus den Quellen nötig ist und im Kontext fehlt. Dann ist query eine "
            + "neue, genauere Suchanfrage in wenigen Stichworten: löse Bezüge aus dem Verlauf auf (Pronomen und "
            + "„das“ durch die gemeinten Begriffe ersetzen), nimm die gesuchte Einzelheit auf und wiederhole keine "
            + "der bisherigen Suchanfragen.";

    /** Prüfung des Wissenskontexts aus früheren Nachrichten derselben Unterhaltung, bevor neu gesucht wird. */
    static final String PRIOR_INSTRUCTION = "Du prüfst für einen Wissens-Chat, ob die Abschnitte, die in dieser "
            + "Unterhaltung bereits gefunden wurden, die aktuelle Nachricht des Nutzers beantworten, ohne neu zu suchen. "
            + "Antworte ausschließlich mit einem JSON-Objekt der Form "
            + "{\"sufficient\": true, \"related\": true, \"query\": \"\"}.\n"
            + "- sufficient = true, wenn die Abschnitte die Nachricht beantworten.\n"
            + "- related = false, wenn die Nachricht ein anderes Thema hat als die Abschnitte.\n"
            + "- Bei sufficient = false ist query eine neue, genauere Suchanfrage in wenigen Stichworten: löse Bezüge "
            + "aus dem Verlauf auf, nimm die gesuchte Einzelheit auf und wiederhole keine der bisherigen Suchanfragen.";

    private static final Pattern SUFFICIENT = Pattern.compile("\"sufficient\"\\s*:\\s*(true|false)");
    private static final Pattern RELATED = Pattern.compile("\"related\"\\s*:\\s*(true|false)");
    private static final Pattern QUERY = Pattern.compile("\"query\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final int maxFollowUps;

    private FollowUpResearch(int maxFollowUps) {
        if (maxFollowUps < 0) {
            throw new IllegalArgumentException("maxFollowUps must not be negative");
        }
        this.maxFollowUps = maxFollowUps;
    }

    /** Keine Nachrecherche: gesucht wird genau einmal mit der Nachricht. */
    public static FollowUpResearch off() {
        return new FollowUpResearch(0);
    }

    /** Höchstens {@code maxFollowUps} Nachsuchen je Nachricht. */
    public static FollowUpResearch upTo(int maxFollowUps) {
        return new FollowUpResearch(maxFollowUps);
    }

    public int maxFollowUps() {
        return maxFollowUps;
    }

    public boolean isEnabled() {
        return maxFollowUps > 0;
    }

    /**
     * @param history  der Verlauf vor der aktuellen Nachricht
     * @param queries  die bisherigen Suchanfragen dieser Nachricht, älteste zuerst
     * @param context  der bisher gefundene Kontextblock; leer, wenn nichts gefunden wurde
     * @return die nächste Suchanfrage oder {@code null}, wenn der Kontext reicht oder kein Urteil zustande kam
     */
    String nextQuery(ChatService chat, List<ChatMessage> history, String message, List<String> queries,
                     String context, ChatOptions options) {
        String verdict;
        try {
            verdict = chat.completeAside(request(history, message, queries, context), options).content();
        } catch (ChatCompletionException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
        String query = parse(verdict);
        if (query == null) {
            return null;
        }
        for (String earlier : queries) {
            if (normalize(earlier).equals(normalize(query))) {
                return null;
            }
        }
        return query;
    }

    /** Urteil über den Wissenskontext früherer Nachrichten. */
    static final class PriorVerdict {

        /** Kein lesbares Urteil (Nebenanfrage gescheitert, unlesbare Antwort): normal suchen. */
        static PriorVerdict unknown() {
            return new PriorVerdict(false, true, null, false);
        }

        private final boolean sufficient;
        private final boolean related;
        private final String query;
        private final boolean known;

        private PriorVerdict(boolean sufficient, boolean related, String query, boolean known) {
            this.sufficient = sufficient;
            this.related = related;
            this.query = query;
            this.known = known;
        }

        boolean isKnown() {
            return known;
        }

        /** Der frühere Kontext beantwortet die Nachricht; es wird nicht gesucht. */
        boolean isSufficient() {
            return known && sufficient;
        }

        /** Der frühere Kontext gehört zum Thema der Nachricht und darf neben neuen Treffern bleiben. */
        boolean isRelated() {
            return related;
        }

        /** Die gezielte Suchanfrage; {@code null}, wenn das Modell keine neue nannte. */
        String query() {
            return query;
        }
    }

    /**
     * Prüft vor jeder Suche, ob der Wissenskontext früherer Nachrichten dieser Unterhaltung die aktuelle Nachricht
     * schon beantwortet (eine Nebenanfrage ohne Werkzeuge, nicht in der Historie).
     *
     * @param queries die Suchanfragen, aus denen der frühere Kontext stammt
     * @param context der frühere Kontextblock
     */
    PriorVerdict checkPrior(ChatService chat, List<ChatMessage> history, String message, List<String> queries,
                            String context, ChatOptions options) {
        String verdict;
        try {
            verdict = chat.completeAside(request(PRIOR_INSTRUCTION, "Bereits gefundener Kontext", history, message,
                    queries, context), options).content();
        } catch (ChatCompletionException e) {
            return PriorVerdict.unknown();
        } catch (RuntimeException e) {
            return PriorVerdict.unknown();
        }
        return parsePrior(verdict, queries);
    }

    static PriorVerdict parsePrior(String verdict, List<String> queries) {
        if (verdict == null) {
            return PriorVerdict.unknown();
        }
        Matcher sufficient = SUFFICIENT.matcher(verdict);
        if (!sufficient.find()) {
            return PriorVerdict.unknown();
        }
        Matcher related = RELATED.matcher(verdict);
        boolean isRelated = !related.find() || Boolean.parseBoolean(related.group(1));
        if (Boolean.parseBoolean(sufficient.group(1))) {
            return new PriorVerdict(true, isRelated, null, true);
        }
        String query = null;
        Matcher found = QUERY.matcher(verdict);
        if (found.find()) {
            String value = unescape(found.group(1)).replace('\r', ' ').replace('\n', ' ').trim();
            query = value.isEmpty() ? null : value;
        }
        if (query != null && queries != null) {
            for (String earlier : queries) {
                if (normalize(earlier).equals(normalize(query))) {
                    query = null; // eine schon gestellte Anfrage brächte dieselben Treffer
                    break;
                }
            }
        }
        return new PriorVerdict(false, isRelated, query, true);
    }

    static List<ChatMessage> request(List<ChatMessage> history, String message, List<String> queries,
                                     String context) {
        return request(INSTRUCTION, "Gefundener Kontext", history, message, queries, context);
    }

    private static List<ChatMessage> request(String instruction, String contextLabel, List<ChatMessage> history,
                                             String message, List<String> queries, String context) {
        StringBuilder text = new StringBuilder();
        List<ChatMessage> recent = history == null ? Collections.<ChatMessage>emptyList() : history;
        List<ChatMessage> shown = new ArrayList<ChatMessage>();
        for (ChatMessage entry : recent) {
            if ((entry.role() == ChatRole.USER || entry.role() == ChatRole.ASSISTANT) && !entry.content().isEmpty()) {
                shown.add(entry);
            }
        }
        if (shown.size() > HISTORY_MESSAGES) {
            shown = shown.subList(shown.size() - HISTORY_MESSAGES, shown.size());
        }
        if (!shown.isEmpty()) {
            text.append("Verlauf:\n");
            for (ChatMessage entry : shown) {
                text.append(entry.role() == ChatRole.USER ? "Nutzer: " : "Assistent: ")
                        .append(shorten(entry.content())).append('\n');
            }
            text.append('\n');
        }
        text.append("Aktuelle Nachricht: ").append(shorten(message)).append("\n\n");
        text.append("Bisherige Suchanfragen:");
        for (String query : queries) {
            text.append("\n- ").append(query);
        }
        text.append("\n\n").append(contextLabel).append(":\n");
        text.append(context == null || context.trim().isEmpty() ? "(nichts gefunden)" : context.trim());
        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        messages.add(ChatMessage.system(instruction));
        messages.add(ChatMessage.user(text.toString()));
        return messages;
    }

    /** Die Suchanfrage aus dem Urteil; {@code null} bei „reicht“, ohne Anfrage oder unlesbar. */
    static String parse(String verdict) {
        if (verdict == null) {
            return null;
        }
        Matcher sufficient = SUFFICIENT.matcher(verdict);
        if (!sufficient.find() || Boolean.parseBoolean(sufficient.group(1))) {
            return null;
        }
        Matcher query = QUERY.matcher(verdict);
        if (!query.find()) {
            return null;
        }
        String value = unescape(query.group(1)).replace('\r', ' ').replace('\n', ' ').trim();
        return value.isEmpty() ? null : value;
    }

    private static String unescape(String json) {
        StringBuilder out = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != '\\' || i + 1 >= json.length()) {
                out.append(c);
                continue;
            }
            char next = json.charAt(++i);
            switch (next) {
                case 'n':
                case 'r':
                case 't':
                    out.append(' ');
                    break;
                case 'u':
                    if (i + 4 < json.length()) {
                        try {
                            out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            // als Text übernehmen
                        }
                    }
                    out.append(next);
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }

    private static String shorten(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() <= MAX_MESSAGE_CHARS ? trimmed : trimmed.substring(0, MAX_MESSAGE_CHARS) + " …";
    }

    private static String normalize(String query) {
        return query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
