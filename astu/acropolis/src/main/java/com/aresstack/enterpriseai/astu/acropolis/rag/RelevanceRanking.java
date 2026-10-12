package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inhaltliches Reranking der Suchtreffer gegen die konkrete Nachricht, bevor sie in den Kontext kommen. Die
 * {@link ReciprocalRankFusion} mischt nur die Ranglisten von Volltext und semantischer Suche; ob ein Abschnitt die
 * Frage tatsächlich beantwortet, bewertet erst diese Stufe.
 *
 * <p>Bewertet wird in einer Nebenanfrage an das Chat-Modell (ohne Werkzeuge, ohne Streaming, nicht in der Historie):
 * alle Kandidaten einer Suche in einem Aufruf, je Abschnitt eine Note von 0 (gehört nicht zur Frage) bis 3 (beantwortet
 * sie direkt). Übernommen werden Abschnitte ab {@link #RELEVANT}, die besten zuerst, bei gleicher Note in
 * Suchreihenfolge. Ein Cross-Encoder (Modellkategorie RERANK) passt hinter dieselbe Stelle, sobald ein ausführbarer
 * Endpunkt dafür angebunden ist.
 *
 * <p>Scheitert die Bewertung (Nebenanfrage fehlgeschlagen, Antwort unlesbar), gelten alle Kandidaten in
 * Suchreihenfolge als relevant; der Turn verhält sich dann wie ohne Reranking. Zustandslos.
 */
public final class RelevanceRanking {

    /** Höchste Note: der Abschnitt beantwortet die Nachricht direkt. */
    public static final int DIRECT = 3;
    /** Ab dieser Note gilt ein Abschnitt als relevant (kippbar). */
    public static final int RELEVANT = 2;
    /** So viele Zeichen eines Abschnitts sieht die Bewertung. */
    static final int MAX_CANDIDATE_CHARS = 900;
    /** So viele der letzten Nachrichten des Verlaufs sieht die Bewertung. */
    static final int HISTORY_MESSAGES = 4;
    static final int MAX_MESSAGE_CHARS = 1000;

    static final String INSTRUCTION = "Du bewertest für einen Wissens-Chat Suchtreffer. Gib für jeden nummerierten "
            + "Abschnitt an, wie viel er zur Antwort auf die aktuelle Nachricht des Nutzers beiträgt (Bezüge aus dem "
            + "Verlauf mitgedacht): 0 = gehört nicht zur Frage, 1 = nur am Rande, 2 = enthält einen Teil der Antwort, "
            + "3 = beantwortet die Frage direkt. Bewerte den Inhalt, nicht die Wortgleichheit. Antworte ausschließlich "
            + "mit einem JSON-Objekt der Form {\"scores\": [{\"id\": 1, \"score\": 2}]} mit einem Eintrag je Abschnitt.";

    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"?(\\d+)\"?\\s*,\\s*\"score\"\\s*:\\s*\"?(\\d)\"?\\s*}");

    private final boolean enabled;

    private RelevanceRanking(boolean enabled) {
        this.enabled = enabled;
    }

    /** Kein Reranking: jeder Treffer gilt als relevant, in Suchreihenfolge. */
    public static RelevanceRanking off() {
        return new RelevanceRanking(false);
    }

    /** Reranking durch das Chat-Modell. */
    public static RelevanceRanking byChatModel() {
        return new RelevanceRanking(true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Ergebnis einer Bewertung: die relevanten Kandidaten, beste zuerst. */
    public static final class Ranked {

        private final List<RetrievedChunk> relevant;
        private final int candidates;
        private final boolean judged;

        Ranked(List<RetrievedChunk> relevant, int candidates, boolean judged) {
            this.relevant = Collections.unmodifiableList(new ArrayList<RetrievedChunk>(relevant));
            this.candidates = candidates;
            this.judged = judged;
        }

        /** Die übernommenen Kandidaten, höchste Note zuerst. */
        public List<RetrievedChunk> relevant() {
            return relevant;
        }

        /** So viele Kandidaten wurden vorgelegt. */
        public int candidates() {
            return candidates;
        }

        /** {@code false}, wenn nicht bewertet wurde (aus oder gescheitert) und alle Kandidaten durchgingen. */
        public boolean judged() {
            return judged;
        }
    }

    /**
     * @param history    der Verlauf vor der Nachricht
     * @param message    die aktuelle Nachricht
     * @param query      die Suchanfrage, die die Kandidaten lieferte
     * @param candidates neue Kandidaten in Suchreihenfolge
     */
    public Ranked rank(ChatService chat, List<ChatMessage> history, String message, String query,
                       List<RetrievedChunk> candidates, ChatOptions options) {
        List<RetrievedChunk> all = candidates == null ? Collections.<RetrievedChunk>emptyList() : candidates;
        if (!enabled || all.isEmpty()) {
            return new Ranked(all, all.size(), false);
        }
        String verdict;
        try {
            verdict = chat.completeAside(request(history, message, query, all), options).content();
        } catch (ChatCompletionException e) {
            return new Ranked(all, all.size(), false);
        } catch (RuntimeException e) {
            return new Ranked(all, all.size(), false);
        }
        Map<Integer, Integer> scores = parse(verdict, all.size());
        if (scores == null) {
            return new Ranked(all, all.size(), false);
        }
        List<RetrievedChunk> relevant = new ArrayList<RetrievedChunk>();
        for (int score = DIRECT; score >= RELEVANT; score--) {
            for (int i = 0; i < all.size(); i++) {
                Integer given = scores.get(i + 1);
                if (given != null && given == score) {
                    relevant.add(all.get(i));
                }
            }
        }
        return new Ranked(relevant, all.size(), true);
    }

    static List<ChatMessage> request(List<ChatMessage> history, String message, String query,
                                     List<RetrievedChunk> candidates) {
        StringBuilder text = new StringBuilder();
        List<ChatMessage> shown = new ArrayList<ChatMessage>();
        if (history != null) {
            for (ChatMessage entry : history) {
                if ((entry.role() == ChatRole.USER || entry.role() == ChatRole.ASSISTANT)
                        && !entry.content().isEmpty()) {
                    shown.add(entry);
                }
            }
        }
        if (shown.size() > HISTORY_MESSAGES) {
            shown = shown.subList(shown.size() - HISTORY_MESSAGES, shown.size());
        }
        if (!shown.isEmpty()) {
            text.append("Verlauf:\n");
            for (ChatMessage entry : shown) {
                text.append(entry.role() == ChatRole.USER ? "Nutzer: " : "Assistent: ")
                        .append(shorten(entry.content(), MAX_MESSAGE_CHARS)).append('\n');
            }
            text.append('\n');
        }
        text.append("Aktuelle Nachricht: ").append(shorten(message, MAX_MESSAGE_CHARS)).append('\n');
        if (query != null && !query.trim().equals(message == null ? "" : message.trim())) {
            text.append("Suchanfrage: ").append(shorten(query, MAX_MESSAGE_CHARS)).append('\n');
        }
        text.append("\nAbschnitte:\n");
        for (int i = 0; i < candidates.size(); i++) {
            RetrievedChunk hit = candidates.get(i);
            String title = hit.resource().title();
            text.append('[').append(i + 1).append("] ")
                    .append(title == null || title.trim().isEmpty() ? hit.resource().id().value() : title.trim())
                    .append('\n')
                    .append(shorten(hit.chunk().text(), MAX_CANDIDATE_CHARS)).append("\n\n");
        }
        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        messages.add(ChatMessage.system(INSTRUCTION));
        messages.add(ChatMessage.user(text.toString().trim()));
        return messages;
    }

    /**
     * Die Noten je Abschnittsnummer (1-basiert); {@code null}, wenn die Antwort keine einzige lesbare Note enthält.
     * Fehlende Abschnitte gelten als 0, Noten über {@link #DIRECT} als {@link #DIRECT}.
     */
    static Map<Integer, Integer> parse(String verdict, int candidates) {
        if (verdict == null) {
            return null;
        }
        Map<Integer, Integer> scores = new HashMap<Integer, Integer>();
        Matcher entry = ENTRY.matcher(verdict);
        while (entry.find()) {
            int id;
            int score;
            try {
                id = Integer.parseInt(entry.group(1));
                score = Integer.parseInt(entry.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            if (id >= 1 && id <= candidates && !scores.containsKey(id)) {
                scores.put(id, Math.min(DIRECT, score));
            }
        }
        return scores.isEmpty() ? null : scores;
    }

    private static String shorten(String text, int max) {
        String trimmed = text == null ? "" : text.replace('\r', ' ').trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + " …";
    }
}
