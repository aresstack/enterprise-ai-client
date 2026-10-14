package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolRegistry;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatToolDefinition;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der Wissens-Werkzeugsatz des normalen Chats ist der des Agenten: {@code knowledge_search} und {@code knowledge_read}
 * aus {@link KnowledgeMcpTools}, über die {@link ChatCompletionsToolLoop}, nur über die angehakten Quellen von jetzt.
 */
public class KnowledgeChatToolsTest {

    private static final Pattern FIRST_ID = Pattern.compile("(?m)^id: (\\S+)$");

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Sonar", "SonarQube einrichten", "SonarQube prüft den Code bei jedem Build.")
            .add("Kaffee", "Kaffeemaschine", "Die Kaffeemaschine wird monatlich entkalkt.");
    private final InMemoryKnowledgeSource abgewaehlt = new InMemoryKnowledgeSource("abgewaehlt")
            .add("SonarRegeln", "SonarQube Regeln intern", "SonarQube Regeln der abgewählten Quelle.");
    private final KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
    private volatile KnowledgeSourceCatalog live = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Sonar", "Kaffee")),
            new KnowledgeSourceRegistration(abgewaehlt, SourceScope.of("SonarRegeln")));
    private final KnowledgeMcpTools knowledge;
    private final List<AiTool> chatTools;

    public KnowledgeChatToolsTest() {
        selection.register(wiki.sourceId(), true);
        selection.register(abgewaehlt.sourceId(), false);
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        knowledge = new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                null), new LoadKnowledgeDocumentUseCase(live).withCatalog(() -> live),
                new SearchDrivenIndexingUseCase(indexing), selection::isSearchable, null);
        chatTools = McpChatTool.all(knowledge.contributions());
    }

    /** Akzeptanztest 1/5: knowledge_search wird angeboten; Beschreibung und Schema wie beim Agenten. */
    @Test
    public void chatGetsTheSameKnowledgeToolsAsTheAgent() {
        assertEquals(5, chatTools.size());
        for (int i = 0; i < chatTools.size(); i++) {
            McpToolContribution agentTool = knowledge.contributions().get(i);
            AiTool chatTool = chatTools.get(i);
            assertEquals(agentTool.getName(), chatTool.name());
            assertEquals(agentTool.getDescription(), chatTool.definition().description());
            for (com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter parameter
                    : agentTool.getParameters()) {
                assertTrue(chatTool.definition().parametersSchema(),
                        chatTool.definition().parametersSchema().contains("\"" + parameter.getName() + "\""));
            }
        }
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_SEARCH, chatTools.get(0).name());
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_TREE, chatTools.get(1).name());
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_READ, chatTools.get(2).name());
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, chatTools.get(3).name());
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_RESOLVE_URL, chatTools.get(4).name());
    }

    /** Akzeptanztests 1, 2 und 5: Suche über die Werkzeug-Schleife, dieselbe Treffer-ID danach mit knowledge_read. */
    @Test
    public void modelSearchesThenReadsTheHitThroughTheToolLoop() {
        SearchThenRead chat = new SearchThenRead("SonarQube");
        ChatResponse response = new ChatCompletionsToolLoop(chat, new ToolRegistry(chatTools), Runnable::run, null,
                ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS).complete(new ChatRequest(
                Collections.singletonList(ChatMessage.user("Gibt es noch weitere Seiten zu SonarQube?")), null));

        assertEquals("Fertig", response.content());
        List<String> offered = new ArrayList<String>();
        for (ChatToolDefinition tool : chat.requests.get(0).tools()) {
            offered.add(tool.name());
        }
        assertTrue(offered.toString(), offered.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH));
        assertTrue(offered.toString(), offered.contains(KnowledgeMcpTools.KNOWLEDGE_READ));
        assertTrue(chat.searchResult, chat.searchResult.contains("id: " + wiki.idOf("Sonar").value()));
        assertEquals(wiki.idOf("Sonar").value(), chat.readId);
        assertTrue(chat.readResult, chat.readResult.contains("prüft den Code bei jedem Build"));
    }

    /**
     * Issue #118: a successful knowledge_read names the read resource as context (source and resource id), a search
     * and a failed read name none.
     */
    @Test
    public void readReportsTheReadResourceAsContextButSearchAndFailedReadDoNot() {
        final List<String> context = new ArrayList<String>();
        com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener activity =
                new com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener() {
                    @Override
                    public void onToolCall(String toolName) {
                    }

                    @Override
                    public void onToolContext(String toolName,
                                              List<com.aresstack.enterpriseai.astu.acropolis.tool.ToolContextResource>
                                                      resources) {
                        for (com.aresstack.enterpriseai.astu.acropolis.tool.ToolContextResource resource : resources) {
                            context.add(toolName + "|" + resource.sourceId() + "|" + resource.resourceId());
                        }
                    }
                };
        ToolRegistry tools = new ToolRegistry(chatTools);
        tools.execute(KnowledgeMcpTools.KNOWLEDGE_SEARCH, "{\"query\":\"SonarQube\"}", activity);
        assertTrue(context.toString(), context.isEmpty());

        String id = wiki.idOf("Sonar").value();
        tools.execute(KnowledgeMcpTools.KNOWLEDGE_READ, "{\"id\":\"" + id + "\"}", activity);
        assertEquals(Collections.singletonList(KnowledgeMcpTools.KNOWLEDGE_READ + "|" + wiki.sourceId().value()
                + "|" + id), context);

        context.clear();
        tools.execute(KnowledgeMcpTools.KNOWLEDGE_READ, "{\"id\":\"" + id + "-missing\"}", activity);
        assertTrue(context.toString(), context.isEmpty());
    }

    /** Akzeptanztest 4: eine abgewählte Quelle wird nicht durchsucht. */
    @Test
    public void deselectedSourceIsNotSearched() {
        String result = search("SonarQube");
        assertTrue(result, result.contains(wiki.idOf("Sonar").value()));
        assertFalse(result, result.contains(abgewaehlt.idOf("SonarRegeln").value()));
        assertFalse(result, result.contains("SonarQube Regeln intern"));
    }

    /** Eine zur Laufzeit hinzugefügte und angehakte Quelle sucht der Chat sofort mit, ohne Neustart. */
    @Test
    public void sourceAddedAtRuntimeIsSearchedWithoutRestart() {
        InMemoryKnowledgeSource neu = new InMemoryKnowledgeSource("neu")
                .add("SonarNeu", "SonarQube Quality Gate", "Das Quality Gate von SonarQube blockiert.");
        assertFalse(search("Quality Gate").contains(neu.idOf("SonarNeu").value()));

        List<KnowledgeSourceRegistration> registrations =
                new ArrayList<KnowledgeSourceRegistration>(live.registrations());
        registrations.add(new KnowledgeSourceRegistration(neu, SourceScope.of("SonarNeu")));
        live = new KnowledgeSourceCatalog(registrations);
        selection.register(neu.sourceId(), true);

        String result = search("Quality Gate");
        assertTrue(result, result.contains(neu.idOf("SonarNeu").value()));
    }

    private String search(String query) {
        return chatTools.get(0).execute("{\"query\":\"" + query + "\"}");
    }

    /** Ruft erst knowledge_search, liest dann den ersten Treffer mit knowledge_read und antwortet. */
    private static final class SearchThenRead implements ChatCompletionPort {
        private final String query;
        final List<ChatRequest> requests = new ArrayList<ChatRequest>();
        String searchResult;
        String readId;
        String readResult;

        SearchThenRead(String query) {
            this.query = query;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            List<ChatMessage> messages = request.messages();
            ChatMessage last = messages.get(messages.size() - 1);
            if (requests.size() == 1) {
                return call(KnowledgeMcpTools.KNOWLEDGE_SEARCH, "{\"query\":\"" + query + "\"}");
            }
            assertEquals(ChatRole.TOOL, last.role());
            if (requests.size() == 2) {
                searchResult = last.content();
                Matcher id = FIRST_ID.matcher(searchResult);
                assertTrue(searchResult, id.find());
                readId = id.group(1);
                return call(KnowledgeMcpTools.KNOWLEDGE_READ, "{\"id\":\"" + readId + "\"}");
            }
            readResult = last.content();
            return new ChatResponse(ChatMessage.assistant("Fertig"), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "m");
        }

        private static ChatResponse call(String tool, String arguments) {
            return new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                    new ChatToolCall("", tool, arguments))), ChatFinishReason.TOOL_CALLS, ChatUsage.notReported(),
                    "m");
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
