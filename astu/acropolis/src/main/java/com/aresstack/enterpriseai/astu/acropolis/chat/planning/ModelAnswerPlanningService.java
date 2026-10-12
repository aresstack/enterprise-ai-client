package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TransientTurnContextFactory;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan the answer in an aside request to the chat model (no tools, no streaming, not in the history): history, source,
 * the local analysis and the current message in, a JSON plan out. An unreadable answer gets one repair request asking
 * for the JSON object alone. If the request fails or the plan stays unreadable, the turn continues without a plan
 * ({@link AnswerPlan#isKnown()} {@code false}, {@link AnswerPlan#failure()} says why).
 */
public final class ModelAnswerPlanningService implements AnswerPlanningService {

    /** Earlier messages the step sees; enough to resolve references like "es" or "dort". */
    static final int HISTORY_MESSAGES = 8;
    /** Longer messages of the history are cut for the step. */
    static final int MAX_MESSAGE_CHARS = 2000;

    static final String INSTRUCTION = "Du planst die Antwort eines Assistenten, bevor er antwortet. Du beantwortest "
            + "die Nachricht nicht selbst und rufst keine Werkzeuge auf. Deine Aufgabe: Was wollte der Nutzer mit "
            + "seiner aktuellen Nachricht höchstwahrscheinlich sagen und erreichen, was braucht die Antwort dafür, und "
            + "was ist der nächste Schritt? Lies die Nachricht im Gespräch, nicht isoliert: Berücksichtige den "
            + "Verlauf, vor allem die letzte Antwort des Assistenten, die Herkunft und den Block "
            + "<client-turn-analysis> als Hinweis zur Form der Nachricht, nicht als Wahrheit.\n"
            + "Bezieht sich die Nachricht auf etwas, das der Assistent gerade gesagt hat (die, das, eben, wie du "
            + "gesagt hast, die genannten), sind die Bezugsobjekte die dort genannten Begriffe; löse den Bezug damit "
            + "auf. Ein Diktat kann Erkennungsfehler enthalten: Ist ein Satz grammatisch auffällig oder passt ein Wort "
            + "weder zur Nachricht noch zum Verlauf, rekonstruiere die wahrscheinlich gemeinte Nachricht aus dem "
            + "Zusammenhang und behandle das auffällige Wort als vermutlichen Erkennungsfehler, nicht als neuen "
            + "Begriff.\n"
            + "Modelliere Unsicherheit, statt sie zu beseitigen. Trenne zwei Fragen: Was hat der Nutzer gesagt "
            + "(utteranceConfidence), und worauf verweist der genannte Begriff (resolutionConfidence)? Eine Abkürzung "
            + "oder Kennung (auch klein geschrieben im Diktat) ist eine mögliche Äußerung, kein Fehler: Schreibe sie "
            + "nie in eine bekanntere um, nur weil dir diese plausibler erscheint. Sie bleibt in interpretedText und "
            + "literalTarget, wie sie dasteht; eine andere Lesart gehört nur nach alternativeInterpretations. Kam der "
            + "Begriff schon früher im Gespräch vor, spricht das zusätzlich für die wörtliche Lesart.\n"
            + "Antworte ausschließlich mit einem JSON-Objekt dieser Form:\n"
            + "{\"interpretedText\": \"\", \"intent\": \"QUESTION\", \"target\": \"\", \"literalTarget\": \"\", "
            + "\"targetKind\": \"NONE\", "
            + "\"informationNeed\": \"UNKNOWN\", \"organizationSpecificMeaningPossible\": false, "
            + "\"asrErrorLikely\": false, \"referencesPreviousAnswer\": false, \"corrections\": [{\"original\": \"\", \"interpretedAs\": \"\", "
            + "\"reason\": \"\"}], \"alternativeInterpretations\": [], \"ambiguity\": \"LOW\", \"utteranceConfidence\": 0.0, "
            + "\"resolutionConfidence\": 0.0, \"nextAction\": \"ANSWER_DIRECTLY\", "
            + "\"knowledgeQuery\": \"\", \"clarificationQuestion\": \"\"}\n"
            + "- interpretedText: die wahrscheinlich gemeinte Nachricht. Korrigiere nur plausible Tipp- oder "
            + "Erkennungsfehler und löse Bezüge aus dem Verlauf auf; sonst übernimm sie unverändert. Erfinde nichts "
            + "hinzu.\n"
            + "- corrections: nur tatsächlich geänderte Stellen bei wahrscheinlichem Erkennungsfehler (der Satz ergibt "
            + "so keinen Sinn, Satzbau und Verlauf deuten gemeinsam auf eine Falscherkennung), sonst []. Nie für eine "
            + "Abkürzung oder Kennung, die so dastehen kann.\n"
            + "- referencesPreviousAnswer: true, wenn die Nachricht sich auf Begriffe oder Aussagen der letzten "
            + "Antwort des Assistenten bezieht.\n"
            + "- intent: GREETING, SMALL_TALK, QUESTION, REQUEST_DEFINITION, REQUEST_EXPLANATION, REQUEST_SEARCH, "
            + "TASK, ASSERTION, FEEDBACK oder UNKNOWN.\n"
            + "- target und targetKind: worum es geht; targetKind ist ACRONYM, IDENTIFIER, TERM, "
            + "CURRENT_APPLICATION (dieses Programm), CONVERSATION_REFERENCE, TOPIC, NONE oder UNKNOWN. literalTarget: "
            + "der Begriff genau so, wie er in der Nachricht steht.\n"
            + "- alternativeInterpretations: andere mögliche Lesarten des Begriffs, etwa eine bekanntere Abkürzung; "
            + "nur Hypothesen, sonst [].\n"
            + "- informationNeed: APPLICATION_HELP (Bedienung, Funktionen, Einstellungen dieses Programms), "
            + "INTERNAL_KNOWLEDGE (Wissen der Organisation), TERM_RESOLUTION (Bedeutung eines Begriffs, der im "
            + "aktuellen Kontext nicht eindeutig aufgelöst ist), GENERAL_KNOWLEDGE (allgemein eindeutiges Wissen), "
            + "NO_INFORMATION_NEEDED (Gruß, Small Talk, Dank, Aussage, Meinung, Aufgabe ohne Wissensbedarf) "
            + "oder UNKNOWN. Eine Aussage oder Meinung über einen Begriff braucht keine Information, auch wenn der "
            + "Begriff unbekannt ist. Eine Korrektur, die eine Frage richtigstellt (\"ich meinte tatsächlich X\"), hat "
            + "den Informationsbedarf dieser Frage, jetzt für X.\n"
            + "- organizationSpecificMeaningPossible: true, wenn ein Begriff (Abkürzung, Kennung, System-, Projekt- "
            + "oder Prozessname) in der Organisation des Nutzers eine eigene Bedeutung haben kann, auch wenn du eine "
            + "öffentliche Bedeutung kennst. Entscheidend ist nicht, ob du irgendeine Bedeutung kennst, sondern ob "
            + "der Begriff hier eindeutig aufgelöst ist.\n"
            + "- ambiguity: LOW, MEDIUM oder HIGH. utteranceConfidence: 0 bis 1, wie sicher du weißt, was der Nutzer "
            + "gesagt und gemeint hat. resolutionConfidence: 0 bis 1, wie sicher du weißt, worauf der genannte Begriff "
            + "hier verweist (niedrig für einen Begriff, der in der Organisation etwas Eigenes bedeuten kann).\n"
            + "- nextAction: ANSWER_DIRECTLY (kein zusätzliches Wissen nötig), USE_APPLICATION_HELP (Frage zu diesem "
            + "Programm), SEARCH_INTERNAL_KNOWLEDGE (Organisationswissen, ein hier nicht aufgelöster, möglicherweise "
            + "organisationsspezifischer Begriff oder ein ausdrücklicher Suchauftrag), RESOLVE_INTERNAL_TERM (die "
            + "Äußerung ist klar, aber ein Begriff ist hier nicht aufgelöst und kann in der Organisation etwas Eigenes "
            + "bedeuten: der Client sucht genau diesen Begriff, ohne Treffer fragt er nach und nennt dabei die "
            + "Alternativen), SEARCH_EXTERNAL_KNOWLEDGE (aktuelles Wissen von außerhalb der Organisation) oder "
            + "ASK_CLARIFICATION.\n"
            + "- ASK_CLARIFICATION nur, wenn verschiedene plausible Lesarten zu wesentlich unterschiedlichen Handlungen "
            + "oder Antworten führen und kein verfügbarer Schritt die Unklarheit auflösen kann, etwa ein Bezug, der "
            + "sich aus dem Verlauf nicht auflösen lässt, oder unklar ist, was getan werden soll. Ein unbekannter "
            + "Begriff ist kein Grund zur Rückfrage: eine interne Suche kann ihn auflösen. Frag nicht wegen jeder "
            + "kleinen Unsicherheit. clarificationQuestion ist dann eine kurze, konkrete Rückfrage an den Nutzer in "
            + "seiner Sprache, sonst \"\".\n"
            + "- knowledgeQuery: bei SEARCH_INTERNAL_KNOWLEDGE eine Suchanfrage in wenigen Stichworten: den Begriff "
            + "unverändert, Bezüge aufgelöst (bei einem Bezug auf die letzte Antwort die dort genannten Begriffe), "
            + "Erkennungsfehler korrigiert, ohne Füllwörter. Sonst \"\". Nie ein Wort, das du für einen "
            + "Erkennungsfehler hältst.";

    static final String REPAIR = "Deine Antwort war kein lesbares JSON-Objekt. Antworte jetzt ausschließlich mit dem "
            + "JSON-Objekt in der beschriebenen Form, ohne weiteren Text.";

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");

    private final ChatService chat;
    private final ChatOptions options;
    private final TransientTurnContextFactory analysis = new TransientTurnContextFactory();

    /** @param options the options of the current turn (model, temperature), or {@code null} for the defaults */
    public ModelAnswerPlanningService(ChatService chat, ChatOptions options) {
        if (chat == null) {
            throw new IllegalArgumentException("chat must not be null");
        }
        this.chat = chat;
        this.options = options;
    }

    @Override
    public AnswerPlan plan(ConversationContext conversationContext, UserMessage userMessage,
                           LinguisticFrame linguisticFrame) {
        List<ChatMessage> request = request(conversationContext, userMessage,
                linguisticFrame == null ? null : analysis.create(linguisticFrame));
        ChatResponse response;
        try {
            response = chat.completeAside(request, options);
        } catch (ChatCompletionException e) {
            return AnswerPlan.failed(userMessage.text(), userMessage.origin(), "request failed: " + e.kind());
        } catch (RuntimeException e) {
            return AnswerPlan.failed(userMessage.text(), userMessage.origin(),
                    "request failed: " + e.getClass().getSimpleName());
        }
        AnswerPlan plan = parse(userMessage, response.content(), response.finishReason());
        if (plan.isKnown()) {
            return plan;
        }
        // The model answered, but not with a readable plan: ask once more for the JSON object alone.
        List<ChatMessage> repair = new ArrayList<ChatMessage>(request);
        repair.add(ChatMessage.assistant(response.content() == null ? "" : response.content()));
        repair.add(ChatMessage.user(REPAIR));
        ChatResponse repaired;
        try {
            repaired = chat.completeAside(repair, options);
        } catch (ChatCompletionException e) {
            return AnswerPlan.failed(userMessage.text(), userMessage.origin(),
                    plan.failure() + "; repair request failed: " + e.kind());
        } catch (RuntimeException e) {
            return AnswerPlan.failed(userMessage.text(), userMessage.origin(),
                    plan.failure() + "; repair request failed: " + e.getClass().getSimpleName());
        }
        AnswerPlan second = parse(userMessage, repaired.content(), repaired.finishReason());
        return second.isKnown() ? second : AnswerPlan.failed(userMessage.text(), userMessage.origin(),
                plan.failure() + "; after repair: " + second.failure());
    }

    static List<ChatMessage> request(ConversationContext context, UserMessage message, String analysisBlock) {
        StringBuilder text = new StringBuilder();
        List<ChatMessage> shown = new ArrayList<ChatMessage>();
        for (ChatMessage entry : context == null ? new ArrayList<ChatMessage>() : context.history()) {
            if ((entry.role() == ChatRole.USER || entry.role() == ChatRole.ASSISTANT) && !entry.content().isEmpty()) {
                shown.add(entry);
            }
        }
        if (shown.size() > HISTORY_MESSAGES) {
            shown = shown.subList(shown.size() - HISTORY_MESSAGES, shown.size());
        }
        // The latest answer of the assistant is the most likely referent of the message: it goes in whole.
        ChatMessage latest = null;
        if (!shown.isEmpty() && shown.get(shown.size() - 1).role() == ChatRole.ASSISTANT) {
            latest = shown.get(shown.size() - 1);
            shown = shown.subList(0, shown.size() - 1);
        }
        if (!shown.isEmpty()) {
            text.append("Verlauf:\n");
            for (ChatMessage entry : shown) {
                text.append(entry.role() == ChatRole.USER ? "Nutzer: " : "Assistent: ")
                        .append(shorten(entry.content())).append('\n');
            }
            text.append('\n');
        }
        if (latest != null) {
            text.append("Letzte Antwort des Assistenten (vollständig):\n").append(latest.content().trim())
                    .append("\n\n");
        }
        text.append("Herkunft der aktuellen Nachricht: ")
                .append(message.origin() == MessageOrigin.SPEECH_TRANSCRIPT ? "SPEECH (Diktat)" : "TEXT (getippt)")
                .append("\n\n");
        if (analysisBlock != null && !analysisBlock.trim().isEmpty()) {
            text.append(analysisBlock.trim()).append("\n\n");
        }
        text.append("Aktuelle Nachricht: ").append(shorten(message.text()));
        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        messages.add(ChatMessage.system(INSTRUCTION));
        messages.add(ChatMessage.user(text.toString()));
        return messages;
    }

    static AnswerPlan parse(UserMessage message, String verdict) {
        return parse(message, verdict, ChatFinishReason.STOP);
    }

    /** Read the plan; an unreadable answer yields an unknown plan whose failure describes its shape, not its text. */
    static AnswerPlan parse(UserMessage message, String verdict, ChatFinishReason finish) {
        if (verdict == null || verdict.trim().isEmpty()) {
            return AnswerPlan.failed(message.text(), message.origin(), "empty answer (finish " + finish + ")");
        }
        int start = verdict.indexOf('{');
        int end = verdict.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return AnswerPlan.failed(message.text(), message.origin(), "no JSON object in " + verdict.length()
                    + " characters (finish " + finish + ")");
        }
        String json = verdict.substring(start, end + 1);
        String intent = first(string(json, "intent"), string(json, "interpretedIntent"));
        String need = string(json, "informationNeed");
        double utterance = number(json, "utteranceConfidence", number(json, "confidence", 0));
        String next = string(json, "nextAction");
        if (intent == null && need == null && next == null) {
            return AnswerPlan.failed(message.text(), message.origin(), "JSON without intent, informationNeed or "
                    + "nextAction in " + verdict.length() + " characters (finish " + finish + ")");
        }
        AnswerPlan.Builder turn = AnswerPlan.builder(message.text(), message.origin())
                .known(true)
                .raw(verdict)
                .interpretedText(oneLine(string(json, "interpretedText")))
                .intent(constant(UserIntent.class, intent, UserIntent.UNKNOWN))
                .target(oneLine(string(json, "target")), constant(TargetKind.class, string(json, "targetKind"),
                        TargetKind.UNKNOWN))
                .informationNeed("NONE".equalsIgnoreCase(need) ? InformationNeed.NO_INFORMATION_NEEDED
                        : constant(InformationNeed.class, need, InformationNeed.UNKNOWN))
                .organizationSpecificMeaningPossible(bool(json, "organizationSpecificMeaningPossible"))
                .likelySpeechRecognitionError(bool(json, "asrErrorLikely") || bool(json, "possibleAsrCorrection"))
                .referencesPreviousAnswer(bool(json, "referencesPreviousAnswer"))
                .ambiguity(constant(Ambiguity.class, string(json, "ambiguity"), Ambiguity.UNKNOWN))
                .literalTarget(oneLine(string(json, "literalTarget")))
                .utteranceConfidence(utterance)
                .resolutionConfidence(number(json, "resolutionConfidence", utterance))
                .knowledgeQuery(oneLine(string(json, "knowledgeQuery")))
                .nextAction(constant(NextAction.class, next, NextAction.UNKNOWN))
                .clarificationQuestion(oneLine(string(json, "clarificationQuestion")));
        Matcher corrections = Pattern.compile("\"corrections\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (corrections.find()) {
            Matcher entry = OBJECT.matcher(corrections.group(1));
            while (entry.find()) {
                String original = string(entry.group(), "original");
                String meant = string(entry.group(), "interpretedAs");
                if (original != null && !original.trim().isEmpty() && meant != null) {
                    turn.hint(new InterpretationHint(oneLine(original), oneLine(meant),
                            oneLine(string(entry.group(), "reason"))));
                }
            }
        }
        Matcher alternatives = Pattern.compile("\"alternativeInterpretations\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL)
                .matcher(json);
        if (alternatives.find()) {
            Matcher entry = STRING.matcher(alternatives.group(1));
            while (entry.find()) {
                turn.alternativeInterpretation(oneLine(unescape(entry.group(1))));
            }
        }
        return turn.build();
    }

    private static String first(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String string(String json, String name) {
        Matcher found = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return found.find() ? unescape(found.group(1)) : null;
    }

    private static boolean bool(String json, String name) {
        Matcher found = Pattern.compile("\"" + name + "\"\\s*:\\s*(true|false)").matcher(json);
        return found.find() && Boolean.parseBoolean(found.group(1));
    }

    private static double number(String json, String name, double fallback) {
        Matcher found = Pattern.compile("\"" + name + "\"\\s*:\\s*\"?(" + NUMBER.pattern() + ")").matcher(json);
        if (!found.find()) {
            return fallback;
        }
        try {
            return Double.parseDouble(found.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E constant(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String oneLine(String value) {
        return value == null ? null : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String shorten(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() <= MAX_MESSAGE_CHARS ? trimmed : trimmed.substring(0, MAX_MESSAGE_CHARS) + " …";
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
                        } catch (NumberFormatException e) {
                            // keep the escape as written
                        }
                    }
                    out.append('\\').append(next);
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }
}
