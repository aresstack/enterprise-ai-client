package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Die gelben Suchvorschläge nach der Recherche: erst wenn ein Turn Seiten tatsächlich gelesen und beantwortet hat,
 * schlägt das Modell Anschluss-Suchen vor, mit der Frage, den gelesenen Auszügen, der Antwort und dem Konzept als
 * einzigem Kontext. Jeder Vorschlag muss danach die Grounding-Prüfung bestehen: jedes seiner Inhaltswörter steht in
 * einer gelesenen Seite oder im Konzept. Was nicht gestützt ist, fällt weg; lieber keine Vorschläge als erfundene.
 * Der neue Satz ersetzt den bisherigen ({@code offer_searches}); bleibt nichts übrig, wird der alte Satz geleert.
 */
final class GroundedSearchTags {

    private static final Logger LOG = Logger.getLogger(GroundedSearchTags.class.getName());
    static final String OFFER_TOOL = "offer_searches";
    static final int MAX_SUGGESTIONS = 5;
    private static final int OUTPUT_TOKENS = 400;
    private static final int ANSWER_CHARS = 3000;
    private static final int CONCEPT_CHARS = 2000;

    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
            "aber", "alle", "als", "auch", "auf", "aus", "bei", "das", "dass", "dem", "den", "der", "des", "die",
            "ein", "eine", "einen", "einer", "für", "fuer", "im", "in", "ist", "mit", "nach", "oder", "über", "ueber",
            "und", "vom", "von", "vs", "zu", "zum", "zur", "the", "and", "for", "of", "to", "with", "on", "an"));

    private final ToolInvoker host;
    private final MainModelChat model;

    GroundedSearchTags(ToolInvoker host, MainModelChat model) {
        this.host = host;
        this.model = model;
    }

    /** Ein Vorschlag (gelber Tag): die Suchanfrage und wozu sie dient. */
    static final class Suggestion {
        final String query;
        final String purpose;

        Suggestion(String query, String purpose) {
            this.query = query;
            this.purpose = purpose;
        }
    }

    /**
     * Erzeugt die Vorschläge aus dem recherchierten Stand und ersetzt damit den bisherigen Satz des Hosts.
     *
     * @param readText der Text der tatsächlich gelesenen Seiten; leer heißt: nichts gelesen, kein neuer Vorschlag
     * @return die angebotenen Vorschläge (leer, wenn nichts gestützt ist oder nichts gelesen wurde)
     */
    List<Suggestion> offerAfterResearch(String question, List<ResearchWriting.Passage> read, String readText,
                                        String answer, String concept, List<String> askedQueries)
            throws ToolInvoker.EndpointUnavailable {
        if (read == null || read.isEmpty() || readText == null || readText.trim().isEmpty()) {
            return new ArrayList<Suggestion>(); // nichts gelesen: keine neuen Vorschläge, der alte Satz bleibt
        }
        List<Suggestion> proposed = parse(complete(question, read, answer, concept));
        List<Suggestion> grounded = grounded(proposed, readText + "\n" + (concept == null ? "" : concept),
                askedQueries, question);
        if (proposed.size() > grounded.size()) {
            LOG.info("[tags] " + (proposed.size() - grounded.size()) + " Vorschläge ohne Beleg in den gelesenen "
                    + "Seiten verworfen");
        }
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("suggestions_json", json(grounded));
        try {
            host.call(OFFER_TOOL, args);
        } catch (ToolInvoker.ToolFailure refused) {
            LOG.info("[tags] Vorschläge nicht angeboten: " + refused.getMessage());
        }
        return grounded;
    }

    private String complete(String question, List<ResearchWriting.Passage> read, String answer, String concept) {
        String system = "Du schlägst Anschluss-Suchen für die Wissensquellen eines Unternehmens vor. Nutze "
                + "AUSSCHLIESSLICH Begriffe, die wörtlich in den GELESENEN AUSZÜGEN oder im KONZEPT stehen; nie eigenes "
                + "Wissen über gleichnamige Produkte, Firmen oder Technologien. Jeder Vorschlag vertieft ein Thema, das "
                + "die Auszüge nennen, aber die Antwort noch nicht ausschöpft. Antworte NUR mit JSON: "
                + "{\"suggestions\": [{\"query\": \"2 bis 4 Wörter\", \"purpose\": \"kurz, wozu\"}]} mit 0 bis "
                + MAX_SUGGESTIONS + " Einträgen. Gibt der Stand keine sinnvolle Anschlussfrage her, liefere eine "
                + "leere Liste. Lieber keine Vorschläge als erfundene.";
        StringBuilder user = new StringBuilder("FRAGE: ").append(question == null ? "" : question.trim())
                .append("\n\nGELESENE AUSZÜGE:\n");
        for (int i = 0; i < read.size(); i++) {
            ResearchWriting.Passage passage = read.get(i);
            user.append("[P").append(i + 1).append("] ").append(passage.title).append(": ")
                    .append(passage.text.replaceAll("\\s*\\n\\s*", " ").trim()).append("\n\n");
        }
        user.append("ANTWORT:\n").append(cut(answer, ANSWER_CHARS)).append('\n');
        if (concept != null && !concept.trim().isEmpty()) {
            user.append("\nKONZEPT:\n").append(cut(concept, CONCEPT_CHARS)).append('\n');
        }
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user.toString()));
        MainModelChatResult result = model.complete(messages, 0.0d, OUTPUT_TOKENS);
        if (!result.isOk()) {
            LOG.warning("[tags] model failed: " + result.getStatus() + " " + result.getDetail());
            return "";
        }
        return result.getText() == null ? "" : result.getText();
    }

    /** Die Vorschläge aus der Modellantwort; unlesbar heißt keine. */
    static List<Suggestion> parse(String text) {
        List<Suggestion> suggestions = new ArrayList<Suggestion>();
        String json = ResearchWriting.clean(text == null ? "" : text);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return suggestions;
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(json.substring(start, end + 1));
        } catch (RuntimeException invalid) {
            return suggestions;
        }
        if (!(parsed instanceof Map) || !(((Map<?, ?>) parsed).get("suggestions") instanceof List)) {
            return suggestions;
        }
        for (Object entry : (List<?>) ((Map<?, ?>) parsed).get("suggestions")) {
            String query = "";
            String purpose = "";
            if (entry instanceof Map) {
                query = text(((Map<?, ?>) entry).get("query"));
                purpose = text(((Map<?, ?>) entry).get("purpose"));
            } else if (entry instanceof String) {
                query = ((String) entry).trim();
            }
            if (!query.isEmpty()) {
                suggestions.add(new Suggestion(query, purpose));
            }
        }
        return suggestions;
    }

    /**
     * Die Grounding-Prüfung: ein Vorschlag bleibt nur, wenn jedes seiner Inhaltswörter im gelesenen Text oder im
     * Konzept vorkommt (Groß-/Kleinschreibung egal, Flexionsendungen tolerant). Doppelte und die eben gestellten
     * Anfragen fallen weg.
     */
    static List<Suggestion> grounded(List<Suggestion> proposed, String evidence, List<String> askedQueries,
                                     String question) {
        String haystack = normalize(evidence);
        Set<String> asked = new HashSet<String>();
        if (askedQueries != null) {
            for (String query : askedQueries) {
                asked.add(normalize(query).trim());
            }
        }
        asked.add(normalize(question).trim());
        Set<String> seen = new HashSet<String>();
        List<Suggestion> kept = new ArrayList<Suggestion>();
        for (Suggestion suggestion : proposed) {
            String key = normalize(suggestion.query).trim().replaceAll("\\s+", " ");
            if (kept.size() >= MAX_SUGGESTIONS || key.isEmpty() || asked.contains(key) || !seen.add(key)) {
                continue;
            }
            if (supported(suggestion.query, haystack)) {
                kept.add(suggestion);
            }
        }
        return kept;
    }

    static boolean supported(String query, String normalizedEvidence) {
        int words = 0;
        for (String word : normalize(query).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 2 || STOP_WORDS.contains(word)) {
                continue;
            }
            words++;
            if (!occurs(word, normalizedEvidence)) {
                return false;
            }
        }
        return words > 0;
    }

    /** Das Wort selbst oder sein Stamm (bis zu drei Endbuchstaben weniger, mindestens fünf Zeichen). */
    private static boolean occurs(String word, String evidence) {
        if (evidence.contains(word)) {
            return true;
        }
        for (int cut = 1; cut <= 3 && word.length() - cut >= 5; cut++) {
            if (evidence.contains(word.substring(0, word.length() - cut))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    static String json(List<Suggestion> suggestions) {
        StringBuilder json = new StringBuilder("[");
        for (Suggestion suggestion : suggestions) {
            json.append(json.length() == 1 ? "" : ",").append("{\"query\":")
                    .append(ResearchHostControl.quote(suggestion.query)).append(",\"purpose\":")
                    .append(ResearchHostControl.quote(suggestion.purpose)).append('}');
        }
        return json.append(']').toString();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String cut(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
