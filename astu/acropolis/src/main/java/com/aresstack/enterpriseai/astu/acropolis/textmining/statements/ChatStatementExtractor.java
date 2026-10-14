package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.Context;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.EventCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.MentionCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.OrderCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.ParticipantCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.RelationCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.Words;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.json.JsonException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link StatementExtractor} over the configured chat model (issue #108): one request per paragraph with numbered
 * words per sentence, a strict JSON answer with word ranges, one repair request if the answer is unreadable. The
 * paragraph goes only to the model the user selected for chat; without one the extractor is unavailable.
 */
public final class ChatStatementExtractor implements StatementExtractor {

    /** Version of instruction and answer format; part of {@link #version()}. */
    static final String PROMPT_VERSION = "1";

    static final String INSTRUCTION = "Du zerlegst Sätze eines Dokuments in überprüfbare Aussagen. Du erfindest "
            + "nichts: Jede Angabe muss wörtlich im angegebenen Satz stehen; kein Weltwissen, keine Ergänzungen, keine "
            + "Schlussfolgerungen. Im Zweifel lässt du eine Angabe weg.\n"
            + "Jeder Satz steht in einer Zeile \"Satz n:\", jedes Wort trägt davor seine Nummer [k]. Wortangaben haben "
            + "die Form {\"from\": k, \"to\": m, \"text\": \"...\"}: erste und letzte Wortnummer im selben Satz und der "
            + "Wortlaut genau so, wie er dort steht.\n"
            + "Antworte ausschließlich mit einem JSON-Objekt dieser Form:\n"
            + "{\"mentions\": [{\"id\": \"m1\", \"sentence\": 1, \"from\": 1, \"to\": 2, \"text\": \"\", "
            + "\"lemma\": \"\", \"pronoun\": false}], "
            + "\"events\": [{\"id\": \"e1\", \"sentence\": 1, \"predicate\": {\"from\": 3, \"to\": 3, \"text\": \"\"}, "
            + "\"particle\": null, \"lemma\": \"\", \"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}], "
            + "\"negation\": null, \"modality\": null, \"condition\": null}], "
            + "\"relations\": [{\"id\": \"r1\", \"sentence\": 1, \"kind\": \"STATE\", \"subject\": \"m2\", "
            + "\"value\": {\"from\": 5, \"to\": 5, \"text\": \"\"}, \"lemma\": \"\", \"negation\": null, "
            + "\"modality\": null, \"condition\": null}], "
            + "\"order\": [{\"before\": \"e1\", \"after\": \"e2\", \"sentence\": 1, "
            + "\"cue\": {\"from\": 9, \"to\": 9, \"text\": \"\"}}]}\n"
            + "- mentions: jede Nominalgruppe, so wie sie im Satz steht, mit Artikel und Adjektiven (\"den "
            + "eingereichten Beleg\"). Ein fester Mehrwortbegriff (\"elektronische Zahlungsanordnung\") ist eine "
            + "einzige Erwähnung. lemma: Grundform des Begriffs im Nominativ Singular ohne Artikel; beschreibende "
            + "Adjektive und Partizipien (zuständig, eingereicht) gehören nicht hinein, sondern in eine Relation; ein "
            + "fester Fachbegriff bleibt vollständig. Pronomen und Relativpronomen (er, sie, es, dieser, der, die, "
            + "das) sind Erwähnungen mit \"pronoun\": true und ohne lemma; löse sie nie auf.\n"
            + "- events: eine je Handlung. predicate: das Vollverb, im Passiv das Partizip (nicht \"wird\"); "
            + "particle: die abgetrennte Verbpartikel (\"aus\" in \"löst ... aus\") oder null; lemma: Infinitiv mit "
            + "Partikel (\"auslösen\"). participants nur aus demselben Satz, role ACTOR (wer handelt; im Passiv die "
            + "Gruppe mit \"durch\" oder \"von\"), AFFECTED_OBJECT (worauf sich die Handlung richtet), RECIPIENT (wer "
            + "etwas erhält), INSTRUMENT (womit), LOCATION (wo). Das grammatische Subjekt ist nicht immer der Akteur. "
            + "Teilen sich koordinierte Verben ein Subjekt (\"prüft ... und löst ... aus\"), ist es bei beiden "
            + "Handlungen dasselbe; das Objekt der einen Handlung wird nie Akteur der anderen. Was der Satz nicht "
            + "eindeutig sagt, lässt du weg.\n"
            + "- relations: kind ATTRIBUTE (dauerhafte Eigenschaft) oder STATE (Zustand, oft ein Partizip); subject "
            + "ist die Erwähnung, value die Wörter der Eigenschaft im selben Satz, lemma ihre Grundform.\n"
            + "- negation, modality, condition: Wörter desselben Satzes (Verneinung wie \"nicht\", Modalverb wie "
            + "\"kann\" oder \"muss\", Bedingung wie \"wenn der Betrag die Grenze übersteigt\") oder null. Eine "
            + "verneinte, modale oder bedingte Aussage hat immer diese Angabe.\n"
            + "- order: nur bei einem ausdrücklichen Signalwort im Satz (anschließend, danach, zuvor, nachdem, "
            + "bevor); before ist die frühere Handlung.\n"
            + "Beispiel: Satz 1: [1]Der [2]Antragsteller [3]reicht [4]das [5]ausgefüllte [6]Formular [7]ein [8]und "
            + "[9]danach [10]prüft [11]die [12]Fachabteilung [13]den [14]Antrag. Erwähnungen m1 (1-2, "
            + "Antragsteller), m2 (4-6, Formular), m3 (11-12, Fachabteilung), m4 (13-14, Antrag); e1 einreichen "
            + "(predicate 3, particle 7, ACTOR m1, AFFECTED_OBJECT m2); e2 prüfen (predicate 10, ACTOR m3, "
            + "AFFECTED_OBJECT m4); r1 STATE m2 \"ausgefüllte\" (ausgefüllt); order e1 vor e2, cue 9.";

    static final String REPAIR = "Deine Antwort war kein lesbares JSON-Objekt. Antworte jetzt ausschließlich mit dem "
            + "JSON-Objekt in der beschriebenen Form, ohne weiteren Text.";

    private final ChatCompletionPort chat;
    private final Supplier<String> model;

    /** @param model the selected chat model ({@code [catalog:]id}); {@code null} or empty: unavailable */
    public ChatStatementExtractor(ChatCompletionPort chat, Supplier<String> model) {
        if (chat == null || model == null) {
            throw new IllegalArgumentException("chat and model must not be null");
        }
        this.chat = chat;
        this.model = model;
    }

    @Override
    public boolean isAvailable() {
        String selected = model.get();
        return selected != null && !selected.trim().isEmpty();
    }

    @Override
    public String version() {
        String selected = model.get();
        return "chat-json/" + PROMPT_VERSION + "/" + (selected == null ? "" : selected.trim());
    }

    @Override
    public StatementCandidates extract(ParagraphText paragraph) {
        String selected = model.get();
        if (selected == null || selected.trim().isEmpty()) {
            throw new StatementExtractionException("no chat model selected");
        }
        ChatOptions options = ChatOptions.builder().model(selected.trim()).build();
        List<ChatMessage> messages = new ArrayList<ChatMessage>(Arrays.asList(ChatMessage.system(INSTRUCTION),
                ChatMessage.user(paragraph.numbered())));
        String answer = ask(messages, options);
        try {
            return parse(answer);
        } catch (StatementExtractionException unreadable) {
            messages.add(ChatMessage.assistant(answer == null ? "" : answer));
            messages.add(ChatMessage.user(REPAIR));
            try {
                return parse(ask(messages, options));
            } catch (StatementExtractionException again) {
                throw new StatementExtractionException(unreadable.getMessage() + "; after repair: "
                        + again.getMessage());
            }
        }
    }

    private String ask(List<ChatMessage> messages, ChatOptions options) {
        try {
            ChatResponse response = chat.complete(new ChatRequest(messages, options));
            return response.content();
        } catch (ChatCompletionException e) {
            throw new StatementExtractionException("request failed: " + e.kind(), e);
        } catch (RuntimeException e) {
            throw new StatementExtractionException("request failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Read the answer; an unreadable one fails with its shape, never its text. */
    static StatementCandidates parse(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new StatementExtractionException("empty answer");
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new StatementExtractionException("no JSON object in " + answer.length() + " characters");
        }
        Map<String, Object> root;
        try {
            root = Json.parseObject(answer.substring(start, end + 1));
        } catch (JsonException e) {
            throw new StatementExtractionException("unreadable JSON: " + e.getMessage());
        }
        if (!root.containsKey("mentions") && !root.containsKey("events") && !root.containsKey("relations")) {
            throw new StatementExtractionException("JSON without mentions, events or relations");
        }
        int malformed = 0;
        List<MentionCandidate> mentions = new ArrayList<MentionCandidate>();
        for (Object entry : Json.array(root, "mentions")) {
            Map<String, Object> object = object(entry);
            Integer sentence = Json.integer(object, "sentence");
            String key = Json.string(object, "id");
            if (object == null || key == null || sentence == null) {
                malformed++;
                continue;
            }
            mentions.add(new MentionCandidate(key, sentence, new Words(number(object, "from"), number(object, "to"),
                    Json.string(object, "text")), Json.string(object, "lemma"), Json.bool(object, "pronoun", false)));
        }
        List<EventCandidate> events = new ArrayList<EventCandidate>();
        for (Object entry : Json.array(root, "events")) {
            Map<String, Object> object = object(entry);
            Integer sentence = Json.integer(object, "sentence");
            String key = Json.string(object, "id");
            Words predicate = words(Json.object(object, "predicate"));
            if (object == null || key == null || sentence == null || predicate == null) {
                malformed++;
                continue;
            }
            List<ParticipantCandidate> participants = new ArrayList<ParticipantCandidate>();
            for (Object participant : Json.array(object, "participants")) {
                Map<String, Object> fields = object(participant);
                if (fields == null || Json.string(fields, "role") == null || Json.string(fields, "mention") == null) {
                    malformed++;
                    continue;
                }
                participants.add(new ParticipantCandidate(Json.string(fields, "role"),
                        Json.string(fields, "mention")));
            }
            events.add(new EventCandidate(key, sentence, predicate, words(Json.object(object, "particle")),
                    Json.string(object, "lemma"), participants, context(object)));
        }
        List<RelationCandidate> relations = new ArrayList<RelationCandidate>();
        for (Object entry : Json.array(root, "relations")) {
            Map<String, Object> object = object(entry);
            Integer sentence = Json.integer(object, "sentence");
            String key = Json.string(object, "id");
            Words value = words(Json.object(object, "value"));
            if (object == null || key == null || sentence == null || value == null) {
                malformed++;
                continue;
            }
            relations.add(new RelationCandidate(key, sentence, Json.string(object, "kind"),
                    Json.string(object, "subject"), value, Json.string(object, "lemma"), context(object)));
        }
        List<OrderCandidate> orders = new ArrayList<OrderCandidate>();
        for (Object entry : Json.array(root, "order")) {
            Map<String, Object> object = object(entry);
            Integer sentence = Json.integer(object, "sentence");
            Words cue = words(Json.object(object, "cue"));
            if (object == null || sentence == null || cue == null || Json.string(object, "before") == null
                    || Json.string(object, "after") == null) {
                malformed++;
                continue;
            }
            orders.add(new OrderCandidate(Json.string(object, "before"), Json.string(object, "after"), sentence,
                    cue));
        }
        return new StatementCandidates(mentions, events, relations, orders, malformed);
    }

    private static Context context(Map<String, Object> object) {
        return new Context(words(Json.object(object, "negation")), words(Json.object(object, "modality")),
                words(Json.object(object, "condition")));
    }

    private static Words words(Map<String, Object> object) {
        if (object == null) {
            return null;
        }
        String text = Json.string(object, "text");
        int first = number(object, "from");
        if ((text == null || text.trim().isEmpty()) && first < 1) {
            return null;
        }
        return new Words(first, number(object, "to") < first ? first : number(object, "to"), text);
    }

    private static int number(Map<String, Object> object, String name) {
        Integer value = Json.integer(object, name);
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
