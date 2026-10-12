package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingListener;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolRegistry;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatToolDefinition;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Herkunft von Quellenangaben wie {@code 【1†p5-L5-L7】}: Der Test zeichnet jede Anfrage auf, die der Client an den
 * Chat-Port gibt (genau das, was der OpenAI-Adapter danach nur noch als JSON serialisiert: Rollen, Inhalte,
 * {@code tool_calls}, {@code tool_call_id}, {@code tools}), und prüft, welche Zitationsbausteine darin vorkommen.
 *
 * <ul>
 *   <li>fester Ablauf (wie v0.35.0) und adaptiver Ablauf mit {@code retrieval.rerank=false}: dieselbe Systemnachricht,
 *       nummerierte Auszüge {@code [n]}, keine Passage-ID, keine Zeilenangabe;</li>
 *   <li>adaptiver Ablauf mit {@code retrieval.rerank=true} (v0.36.0): nur die kleinste ausreichende Menge, hier genau
 *       {@code [1]};</li>
 *   <li>Werkzeugpfad: erst die Ergebnisse von {@code knowledge_search}/{@code knowledge_read} bringen
 *       Passage-IDs mit {@code #p<Position>} in den Modell-Input; Zeilennummern ({@code L<n>}), {@code 【} und
 *       {@code †} erzeugt der Client in keinem Pfad.</li>
 * </ul>
 */
public class CitationProvenanceTest {

    private static final String QUESTION = "Wie oft wird die Kaffeemaschine entkalkt?";
    /** Eine Zeilenangabe wie {@code L5}; Passage-IDs enthalten nur Kleinbuchstaben-Hex. */
    private static final Pattern LINE_REFERENCE = Pattern.compile("\\bL\\d+\\b");
    private static final Pattern PASSAGE_POSITION = Pattern.compile("#p\\d+@");
    private static final Pattern CANDIDATE = Pattern.compile("^\\[(c\\d+)] resource: (\\S+) ", Pattern.MULTILINE);

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Kaffee", "Kaffeemaschine pflegen", "Die Kaffeemaschine wird monatlich entkalkt. "
                    + "Dafür nimmt man Zitronensäure. Danach spült man zweimal mit klarem Wasser.")
            .add("Bohnen", "Kaffeebohnen", "Die Kaffeemaschine braucht frisch gemahlene Bohnen.")
            .add("Reinigung", "Kaffeemaschine reinigen", "Die Kaffeemaschine wird täglich gespült.");
    private final KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Kaffee", "Bohnen", "Reinigung")));
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final RetrieveKnowledgeUseCase retrieval = new RetrieveKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), null);
    private final RecordingModel model = new RecordingModel();
    private final ChatService chat = new ChatService(model);

    @Before
    public void indexAll() {
        indexing.indexSource(wiki, SourceScope.of("Kaffee", "Bohnen", "Reinigung"), IndexingListener.none());
    }

    /** v0.35.0 (fester Ablauf) und v0.36.0 mit rerank=false schicken denselben Kontext, nur nummerierte Auszüge. */
    @Test
    public void fixedPathAndRerankOffSendTheSameNumberedExcerpts() throws Exception {
        ChatRequest v035 = answerRequest(new RagChatUseCase(chat, retrieval,
                new PromptContextAssembler(ContextSettings.defaults()), RetrievalPreparation.none(),
                FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS)), null);
        ChatRequest rerankOff = answerRequest(adaptive(EvidencePolicy.defaults().withReranking(false)), null);

        String system = v035.messages().get(0).content();
        assertEquals("rerank=false = Ablauf von v0.35.0", system, rerankOff.messages().get(0).content());
        assertTrue(system, system.contains(ContextSettings.DEFAULT_INSTRUCTION));
        assertTrue(system, system.contains("[1] Kaffeemaschine pflegen"));
        assertTrue("fester Ablauf: mehrere Auszüge\n" + system, system.contains("\n[2] "));
        assertTrue(v035.tools().isEmpty());
        assertNoCitationSyntax(v035);
        assertFalse("keine Passage-ID im RAG-Kontext\n" + system, PASSAGE_POSITION.matcher(system).find());
    }

    /** v0.36.0 mit rerank=true: der Bewerter nennt einen Abschnitt, nur er kommt als [1] in den Kontext. */
    @Test
    public void rerankOnSendsOnlyTheMinimumEvidence() throws Exception {
        ChatRequest request = answerRequest(adaptive(EvidencePolicy.defaults()), null);

        String system = request.messages().get(0).content();
        assertEquals("ein Bewerter-Aufruf", 1, model.evaluations);
        assertTrue(system, system.contains("[1] Kaffeemaschine pflegen"));
        assertFalse("nur die kleinste ausreichende Menge\n" + system, system.contains("\n[2] "));
        assertTrue(system, system.contains("knowledge_read"));
        assertNoCitationSyntax(request);
        assertFalse(system, PASSAGE_POSITION.matcher(system).find());
        for (ChatRequest aside : model.asides) {
            assertNoCitationSyntax(aside);
        }
    }

    /**
     * Werkzeugpfad (Chat mit Wissens-Werkzeugen, wie RagChatBinding ihn baut): knowledge_search nummeriert Treffer mit
     * {@code [n]} und nennt ihre Passage-ID, knowledge_read liefert Passagen als {@code [passage …#p<n>@…]}. Das ist die
     * einzige Stelle, an der {@code p<n>} in den Modell-Input kommt; {@code L<n>}, {@code 【} und {@code †} nie.
     */
    @Test
    public void toolResultsCarryPassagePositionsButNoLineNumbers() throws Exception {
        SearchDrivenIndexingUseCase live = new SearchDrivenIndexingUseCase(indexing);
        KnowledgeMcpTools knowledge = new KnowledgeMcpTools(retrieval, new LoadKnowledgeDocumentUseCase(catalog), live,
                null);
        model.toolScript.add(call("knowledge_search", "query", "Kaffeemaschine entkalken"));
        model.toolScript.add(null); // knowledge_read mit der Passage-ID aus dem Suchergebnis, sonst der Seiten-ID
        ChatCompletionsToolLoop loop = new ChatCompletionsToolLoop(model, new ToolRegistry(Arrays.asList(
                new McpTool(knowledge.knowledgeSearch()), new McpTool(knowledge.knowledgeRead()))), Runnable::run,
                null, ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS);

        ChatRequest last = answerRequest(adaptive(EvidencePolicy.defaults()), loop);

        assertEquals("Suche, Lesen, Antwort", 3, model.answerRounds.size());
        List<String> toolResults = new ArrayList<String>();
        for (ChatMessage message : last.messages()) {
            if (message.toolCallId() != null && !message.toolCallId().isEmpty()) {
                toolResults.add(message.content());
            }
        }
        assertEquals(2, toolResults.size());
        String search = toolResults.get(0);
        String read = toolResults.get(1);
        assertTrue(search, search.contains("[1] "));
        assertTrue(read, read.contains("[passage " + wiki.idOf("Kaffee").value() + "@"));
        assertTrue("Passageposition im Werkzeugergebnis\n" + read, PASSAGE_POSITION.matcher(read).find());
        assertNoCitationSyntax(last);
        assertEquals(2, last.tools().size());
    }

    private RagChatUseCase adaptive(EvidencePolicy policy) {
        return new RagChatUseCase(chat, retrieval, new PromptContextAssembler(ContextSettings.defaults()),
                RetrievalPreparation.none(), FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS),
                () -> policy);
    }

    /** Schickt die Frage und liefert die letzte Anfrage des Antwortturns (die, nach der das Modell antwortet). */
    private ChatRequest answerRequest(RagChatUseCase rag, ChatCompletionPort via) throws Exception {
        model.answerRounds.clear();
        ChatConversationId id = chat.openConversation("Du bist ein hilfreicher Assistent.");
        RecordingListener listener = new RecordingListener();
        rag.send(id, QUESTION, RagOptions.enabled(), null, listener, null, via, RetrievalProgress.none());
        assertEquals("completed", listener.await().outcome());
        return model.answerRounds.get(model.answerRounds.size() - 1);
    }

    /** Alles, was die Anfrage dem Modell zeigt, ohne die Zeichen des Modell-Zitierformats. */
    private static void assertNoCitationSyntax(ChatRequest request) {
        String input = dump(request);
        assertFalse("【 im Modell-Input\n" + input, input.contains("【") || input.contains("】"));
        assertFalse("† im Modell-Input\n" + input, input.contains("†"));
        Matcher line = LINE_REFERENCE.matcher(input);
        assertFalse("Zeilenangabe im Modell-Input: " + (line.find() ? line.group() : "") + "\n" + input,
                LINE_REFERENCE.matcher(input).find());
    }

    /** Die Anfrage in der Form, die der Adapter serialisiert (Rolle, Inhalt, Aufrufe, Werkzeuge). */
    private static String dump(ChatRequest request) {
        StringBuilder out = new StringBuilder();
        for (ChatMessage message : request.messages()) {
            out.append("--- ").append(message.role()).append('\n').append(message.content()).append('\n');
            if (message.toolCallId() != null) {
                out.append("tool_call_id: ").append(message.toolCallId()).append('\n');
            }
            for (ChatToolCall call : message.toolCalls()) {
                out.append("tool_call: ").append(call.name()).append(' ').append(call.argumentsJson()).append('\n');
            }
        }
        for (ChatToolDefinition tool : request.tools()) {
            out.append("--- tool ").append(tool.name()).append('\n').append(tool.description()).append('\n')
                    .append(tool.parametersSchemaJson()).append('\n');
        }
        return out.toString();
    }

    private static ChatResponse call(String tool, String argument, String value) {
        return new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(new ChatToolCall(
                "call_" + tool, tool, "{\"" + argument + "\":\"" + value + "\"}"))), ChatFinishReason.TOOL_CALLS,
                ChatUsage.notReported(), "m");
    }

    /** Ein Wissens-Werkzeug im Chat-Pfad, wie die Composition Root es über {@code McpChatTool} anbietet. */
    private static final class McpTool implements AiTool {

        private static final Pattern ARGUMENT = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

        private final McpToolContribution tool;

        McpTool(McpToolContribution tool) {
            this.tool = tool;
        }

        @Override
        public String name() {
            return tool.getName();
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.function(tool.getName(), tool.getDescription(), "{\"type\":\"object\"}");
        }

        @Override
        public String execute(String arguments) {
            Map<String, Object> values = new HashMap<String, Object>();
            Matcher argument = ARGUMENT.matcher(arguments);
            while (argument.find()) {
                values.put(argument.group(1), argument.group(2));
            }
            return tool.getHandler().invoke(new McpToolCall(tool.getName(), values)).getText();
        }
    }

    /**
     * Das Modell: Nebenanfragen (Evidenzbewertung, Nachrecherche-Prüfung) beantwortet es nach ihrer Anweisung, den
     * Antwortturn zeichnet es je Runde auf; im Werkzeugpfad verlangt es erst die geskripteten Aufrufe.
     */
    private final class RecordingModel implements ChatCompletionPort {

        final List<ChatRequest> asides = new ArrayList<ChatRequest>();
        final List<ChatRequest> answerRounds = new ArrayList<ChatRequest>();
        final List<ChatResponse> toolScript = new ArrayList<ChatResponse>();
        int evaluations;

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            asides.add(request);
            String instruction = request.messages().get(0).content();
            String answer;
            if (instruction.equals(EvidenceEvaluator.INSTRUCTION)) {
                evaluations++;
                answer = evaluation(request.messages().get(1).content());
            } else {
                answer = "{\"sufficient\": true, \"query\": \"\"}";
            }
            return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "m");
        }

        /** Alle Kandidaten relevant; die kleinste ausreichende Menge ist der Abschnitt der Pflegeseite. */
        private String evaluation(String user) {
            Map<String, String> offered = new LinkedHashMap<String, String>();
            Matcher candidate = CANDIDATE.matcher(user);
            while (candidate.find()) {
                offered.put(candidate.group(1), candidate.group(2));
            }
            StringBuilder ranked = new StringBuilder();
            String minimum = null;
            for (Map.Entry<String, String> entry : offered.entrySet()) {
                ranked.append(ranked.length() == 0 ? "" : ", ").append("{\"id\": \"").append(entry.getKey())
                        .append("\", \"relevant\": true}");
                if (minimum == null && entry.getValue().equals(wiki.idOf("Kaffee").value())) {
                    minimum = entry.getKey();
                }
            }
            assertTrue("Pflegeseite unter den Kandidaten\n" + user, minimum != null);
            return "{\"ranked\": [" + ranked + "], \"minimumEvidence\": [\"" + minimum + "\"], \"sufficient\": true, "
                    + "\"missing\": \"\", \"nextQuery\": \"\"}";
        }

        @Override
        public synchronized ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            answerRounds.add(request);
            ChatResponse response;
            if (answerRounds.size() <= toolScript.size()) {
                response = toolScript.get(answerRounds.size() - 1);
                if (response == null) {
                    response = call("knowledge_read", "id", readTarget(request));
                }
            } else {
                response = new ChatResponse(ChatMessage.assistant("Monatlich [1]."), ChatFinishReason.STOP,
                        ChatUsage.notReported(), "m");
            }
            listener.onStart();
            if (!response.content().isEmpty()) {
                listener.onDelta(response.content());
            }
            listener.onComplete(response);
            return new ChatTask() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }

        /** Die Passage-ID des ersten Suchtreffers, sonst die Seiten-ID. */
        private String readTarget(ChatRequest request) {
            String result = request.messages().get(request.messages().size() - 1).content();
            Matcher passage = Pattern.compile("^passage: (\\S+)$", Pattern.MULTILINE).matcher(result);
            return passage.find() ? passage.group(1) : wiki.idOf("Kaffee").value();
        }
    }
}
