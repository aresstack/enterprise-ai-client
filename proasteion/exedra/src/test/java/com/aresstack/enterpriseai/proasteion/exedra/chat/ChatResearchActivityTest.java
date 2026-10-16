package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanningPolicy;
import com.aresstack.enterpriseai.astu.acropolis.rag.FollowUpResearch;
import com.aresstack.enterpriseai.astu.acropolis.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalTriage;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPreparation;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.EDT;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.awaitIdle;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.entries;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der normale Chat zeigt seine Arbeit wie der Agent: Recherchekarte und Werkzeugwolken über das gemeinsame
 * Activity-System des {@link ChatShellModel}, nie als Text in der Antwortblase („Wissen wird gesucht …“).
 */
public class ChatResearchActivityTest {

    private final ExecutorService workers = UiTestSupport.workers("chat-research-activity-test");
    private final FakeChatCompletionPort port = new FakeChatCompletionPort();
    private final ChatService chat = new ChatService(port);
    private final ChatConversationId conversation = chat.openConversation("Antworte knapp.");
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final EmbeddingModelIdentity space = embeddings.modelIdentity();
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("handbuch")
            .add("urlaub", "Urlaubsregelung", "Mitarbeiter haben dreißig Tage Urlaub im Jahr. Resturlaub verfällt "
                    + "am 31. März des Folgejahres.")
            .add("kuendigung", "Kündigungsfrist", "Die Kündigungsfrist beträgt drei Monate zum Quartalsende. Sie "
                    + "gilt für beide Seiten.");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    @Test
    public void ragSearchShowsAResearchActivityInsteadOfAnAssistantPlaceholder() throws Exception {
        indexAll();
        final RagChatBindingTest.GatedIndex gated = new RagChatBindingTest.GatedIndex(index);
        port.enqueueAnswer("Drei Monate [1].");
        final Shell shell = shell(gated, FollowUpResearch.off());
        send(shell, "Wie lange ist die Kündigungsfrist?");
        assertTrue(gated.awaitSearch());

        List<TranscriptEntry> during = onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return new ArrayList<TranscriptEntry>(shell.model.getEntries());
            }
        });
        assertEquals(during.toString(), 3, during.size());
        assertEquals(TranscriptEntry.Author.USER, during.get(0).getAuthor());
        TranscriptEntry card = during.get(1);
        assertEquals(TranscriptEntry.Author.ACTIVITY, card.getAuthor());
        assertEquals(ChatResearchCard.TITLE, card.getActivityTitle());
        assertEquals("gelbe Werkzeugwolke, keine Denkwolke", ChatResearchCard.KIND, card.getActivityKind());
        assertFalse(card.isThinking());
        TranscriptEntry answer = during.get(2);
        assertEquals(TranscriptEntry.Author.ASSISTANT, answer.getAuthor());
        assertEquals("", answer.getText());
        assertEquals("kein Status in der Antwortblase", "", answer.getActivity());
        assertTrue("leere Antwort hinter der Karte bleibt unsichtbar", answer.isPendingBehindActivity());

        gated.release();
        awaitIdle(shell.model);
        assertEquals("Drei Monate [1].", entries(shell.model).get(1).getText());
    }

    @Test
    public void progressUpdatesTheSameActivityInPlace() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate [1].");
        Shell shell = shell(index, FollowUpResearch.off());
        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        assertEquals("genau eine Karte", 1, shell.recorder.addedActivities.size());
        List<String> states = shell.recorder.textsOf(shell.recorder.addedActivities.get(0));
        assertTrue(states.toString(), containsFragment(states, "Suche 1 / 1: „Wie lange ist die Kündigungsfrist?“"));
        assertTrue(states.toString(), containsFragment(states, ChatResearchCard.LIVE_SEARCH));
        assertTrue(states.toString(), containsFragment(states, ChatResearchCard.KEYWORD_SEARCH));
        assertTrue(states.toString(), containsFragment(states, ChatResearchCard.SEMANTIC_SEARCH));
        assertTrue(states.toString(), containsFragment(states, "Treffer: "));
        assertTrue(states.toString(), containsFragment(states, ChatResearchCard.PREPARING));
        for (String state : states) {
            assertFalse("keine erfundenen Seitenzähler: " + state, state.contains("Seiten"));
        }
    }

    @Test
    public void followUpSearchAppearsAsAnotherSearchStepOfTheSameCard() throws Exception {
        indexAll();
        port.enqueueAnswer("{\"sufficient\": false, \"query\": \"Kündigung Frist Quartal\"}");
        port.enqueueAnswer("{\"sufficient\": true, \"query\": \"\"}");
        port.enqueueAnswer("Drei Monate [1].");
        Shell shell = shell(index, FollowUpResearch.upTo(2));
        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        assertEquals(1, shell.recorder.addedActivities.size());
        List<String> states = shell.recorder.textsOf(shell.recorder.addedActivities.get(0));
        String last = states.get(states.size() - 1);
        assertTrue(last, last.contains("Suche 1 / 3: „Wie lange ist die Kündigungsfrist?“"));
        assertTrue(last, last.contains(ChatResearchCard.NOT_ENOUGH));
        assertTrue(last, last.contains("Suche 2 / 3: „Kündigung Frist Quartal“"));
        assertTrue(states.toString(), containsFragment(states, ChatResearchCard.CHECKING));
        assertFalse("die Begründung des Modells erscheint nicht", containsFragment(states, "sufficient"));
        assertEquals("Drei Monate [1].", entries(shell.model).get(1).getText());
    }

    @Test
    public void knowledgeToolsAppearAsToolCloudsOfTheSharedActivityPath() throws Exception {
        indexAll();
        final ScriptedChat toolChat = new ScriptedChat()
                .call("knowledge_search", "{\"query\":\"Kündigung\"}")
                .call(ChatBookmark.READ_TOOL, "{\"id\":\"confluence://cf.intern/confluence/page/4242001\"}")
                .answer("Drei Monate.");
        final Shell shell = shell(index, FollowUpResearch.off());
        onEdt(new Runnable() {
            @Override
            public void run() {
                shell.binding.enableTools(new ToolSupport(toolChat, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", Arrays.asList(tool("knowledge_search", "id: wiki:frist", true),
                        tool(ChatBookmark.READ_TOOL, "SEITE", true)), false));
                shell.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true,
                        Collections.<java.nio.file.Path>emptyList(), Collections.singletonList(
                                new com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage("Betriebshandbuch",
                                        "confluence://cf.intern/confluence/page/4242001")));
            }
        });
        awaitIdle(shell.model);

        List<TranscriptEntry> clouds = shell.recorder.addedActivities;
        assertEquals(clouds.toString(), 3, clouds.size());
        assertEquals(ChatResearchCard.TITLE, clouds.get(0).getActivityTitle());
        assertEquals(RagChatBinding.SEARCHING_KNOWLEDGE_ACTIVITY, clouds.get(1).getActivityTitle());
        assertEquals("„Kündigung“", shell.recorder.textsOf(clouds.get(1)).get(0));
        assertEquals(RagChatBinding.READING_PAGE_ACTIVITY, clouds.get(2).getActivityTitle());
        assertEquals("Titel der Seite des Chats statt Kennung", "„Betriebshandbuch“",
                shell.recorder.textsOf(clouds.get(2)).get(0));
        assertEquals("Drei Monate.", entries(shell.model).get(1).getText());
    }

    @Test
    public void firstAssistantBubbleContainsOnlyTheModelAnswer() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei", " Monate [1].");
        Shell shell = shell(index, FollowUpResearch.off());
        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        assertFalse(shell.recorder.assistantStates.isEmpty());
        for (String shown : shell.recorder.assistantStates) {
            assertTrue("nur Antworttext: " + shown, shown.isEmpty() || "Drei Monate [1].".startsWith(shown));
        }
        assertEquals("Drei Monate [1].", entries(shell.model).get(1).getText());
    }

    @Test
    public void successfulActivitiesDisappearWhenTheTurnEnds() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate [1].");
        Shell shell = shell(index, FollowUpResearch.off());
        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        assertEquals(1, shell.recorder.addedActivities.size());
        List<TranscriptEntry> entries = entries(shell.model);
        assertEquals(entries.toString(), 2, entries.size());
        assertEquals(TranscriptEntry.Author.USER, entries.get(0).getAuthor());
        assertEquals(TranscriptEntry.Author.ASSISTANT, entries.get(1).getAuthor());
        assertTrue("Quellen an der Antwort", entries.get(1).hasSources());
        String summary = shell.recorder.addedActivities.get(0).getActivitySummary();
        assertTrue(summary, summary.startsWith("Recherche abgeschlossen · "));
        assertTrue(summary, summary.contains(" Seite"));
        assertTrue(summary, summary.contains(" geprüft · "));
    }

    @Test
    public void failedRelevantActivityStaysVisibleWhenTheTurnFails() throws Exception {
        port.enqueueError(new ChatCompletionException(ChatErrorKind.TRANSPORT, "connection refused"));
        Shell shell = shell(new RagChatBindingTest.FailingIndex(), FollowUpResearch.off());
        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        List<TranscriptEntry> entries = entries(shell.model);
        TranscriptEntry card = null;
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                card = entry;
            }
        }
        assertTrue("ausgefallene Suche bleibt sichtbar: " + entries, card != null);
        assertEquals(TranscriptEntry.State.FAILED, card.getState());
        assertTrue(card.getText(), card.getText().contains(ChatResearchCard.FAILED_LINE));
    }

    @Test
    public void stopDuringRetrievalSkipsFurtherStepsAndRemovesTheCard() throws Exception {
        indexAll();
        final RagChatBindingTest.GatedIndex gated = new RagChatBindingTest.GatedIndex(index);
        port.enqueueHanging("Zu spät");
        final Shell shell = shell(gated, FollowUpResearch.upTo(2));
        send(shell, "Wie lange ist die Kündigungsfrist?");
        assertTrue(gated.awaitSearch());
        onEdt(new Runnable() {
            @Override
            public void run() {
                shell.binding.stopRequested();
            }
        });
        List<String> states = shell.recorder.textsOf(shell.recorder.addedActivities.get(0));
        assertTrue(states.toString(), states.get(states.size() - 1).contains(ChatResearchCard.CANCELLING));
        gated.release();
        awaitIdle(shell.model);

        List<TranscriptEntry> entries = entries(shell.model);
        assertEquals(entries.toString(), 2, entries.size());
        assertEquals(TranscriptEntry.State.CANCELLED, entries.get(1).getState());
        assertEquals("keine Nachsuche nach dem Stop (nur die Antwort ging an das Modell)", 1, port.requests().size());
        String summary = shell.recorder.addedActivities.get(0).getActivitySummary();
        assertTrue(summary, summary.startsWith("Recherche abgebrochen"));
        assertFalse(chat.isBusy(conversation));
    }

    /** Small talk: the model's triage decides against a search, so no research card appears at all. */
    @Test
    public void smallTalkShowsNoResearchCard() throws Exception {
        indexAll();
        port.enqueueAnswer("{\"search\": false, \"query\": \"\"}");
        port.enqueueAnswer("Hallo zurück");
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null),
                RetrievalPreparation.none(), FollowUpResearch.upTo(2), null, RetrievalTriage.byModel());
        Shell shell = onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                return new Shell(rag);
            }
        });
        send(shell, "Hi");
        awaitIdle(shell.model);

        assertTrue(shell.recorder.addedActivities.toString(), shell.recorder.addedActivities.isEmpty());
        assertEquals("one triage, one answer", 2, port.requests().size());
        assertEquals(2, entries(shell.model).size());
    }

    /** Every planned turn starts with the planning bubble; a greeting shows nothing else, a search adds the card. */
    @Test
    public void planningComesFirstAndOnlyAPlannedSearchShowsTheCard() throws Exception {
        indexAll();
        port.enqueueAnswer("{\"intent\": \"GREETING\", \"informationNeed\": \"NO_INFORMATION_NEEDED\", "
                + "\"confidence\": 0.99, \"nextAction\": \"ANSWER_DIRECTLY\"}");
        port.enqueueAnswer("Hallo zurück");
        port.enqueueAnswer("{\"intent\": \"QUESTION\", \"informationNeed\": \"INTERNAL_KNOWLEDGE\", "
                + "\"confidence\": 0.9, \"nextAction\": \"SEARCH_INTERNAL_KNOWLEDGE\", "
                + "\"knowledgeQuery\": \"Kündigungsfrist\"}");
        port.enqueueAnswer("Drei Monate [1].");
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null),
                RetrievalPreparation.none(), FollowUpResearch.off(), null, RetrievalTriage.byModel(), null, null,
                () -> AnswerPlanningPolicy.defaults());
        Shell shell = onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                return new Shell(rag);
            }
        });
        send(shell, "Hi");
        awaitIdle(shell.model);

        assertEquals(shell.recorder.addedActivities.toString(), 1, shell.recorder.addedActivities.size());
        TranscriptEntry planning = shell.recorder.addedActivities.get(0);
        assertEquals(RagChatBinding.PLANNING_ACTIVITY, planning.getActivityTitle());
        assertTrue(planning.isThinking());

        send(shell, "Wie lange ist die Kündigungsfrist?");
        awaitIdle(shell.model);

        assertEquals(shell.recorder.addedActivities.toString(), 3, shell.recorder.addedActivities.size());
        assertEquals(RagChatBinding.PLANNING_ACTIVITY, shell.recorder.addedActivities.get(1).getActivityTitle());
        assertEquals(ChatResearchCard.TITLE, shell.recorder.addedActivities.get(2).getActivityTitle());
        List<String> states = shell.recorder.textsOf(shell.recorder.addedActivities.get(2));
        assertTrue(states.toString(), containsFragment(states, "Suche 1 / 1: „Kündigungsfrist“"));
        assertEquals("plan and answer, twice", 4, port.requests().size());

        // Without a readable plan the answer decides alone: no triage request, no research card.
        port.enqueueAnswer("Das kann ich nicht sagen.");
        port.enqueueAnswer("Immer noch nicht.");
        port.enqueueAnswer("Drei Monate.");
        send(shell, "Und die Probezeit?");
        awaitIdle(shell.model);

        assertEquals(shell.recorder.addedActivities.toString(), 4, shell.recorder.addedActivities.size());
        assertEquals(RagChatBinding.PLANNING_ACTIVITY, shell.recorder.addedActivities.get(3).getActivityTitle());
        assertEquals("plan, repair, answer: no triage", 7, port.requests().size());
    }

    @Test
    public void capabilityQuestionShowsNoResearchCard() throws Exception {
        indexAll();
        Shell shell = shell(index, FollowUpResearch.upTo(2));
        send(shell, "Kannst Du auch Recherchieren? Hast Du MCP Tools? Welche?");
        awaitIdle(shell.model);

        assertTrue(shell.recorder.addedActivities.toString(), shell.recorder.addedActivities.isEmpty());
        assertEquals("Antwort schreibt der Client, kein Modellaufruf", 0, port.requests().size());
    }

    // --- Hilfen ---

    private void indexAll() {
        new IndexKnowledgeUseCase(index, embeddings, space, new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .indexSource(source, SourceScope.of("urlaub", "kuendigung"), null);
    }

    private Shell shell(KnowledgeIndexPort indexPort, FollowUpResearch followUps) throws Exception {
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(indexPort, embeddings, space, null), new PromptContextAssembler(null),
                RetrievalPreparation.none(), followUps);
        return onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                return new Shell(rag);
            }
        });
    }

    private static void send(final Shell shell, final String text) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                shell.binding.sendRequested(text, true);
            }
        });
    }

    private static boolean containsFragment(List<String> states, String fragment) {
        for (String state : states) {
            if (state.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static AiTool tool(final String name, final String result, final boolean succeeds) {
        return new AiTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.function(name, name, "{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public String execute(String arguments) {
                if (!succeeds) {
                    throw new IllegalStateException("Quelle nicht erreichbar");
                }
                return result;
            }
        };
    }

    private final class Shell {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final Recorder recorder = new Recorder();
        final RagChatBinding binding;

        Shell(RagChatUseCase rag) {
            model.addListener(recorder);
            binding = new RagChatBinding(rag, conversation, model, EDT, workers, ZoneOffset.UTC);
        }
    }

    /** Merkt sich jede hinzugekommene Wolke und jeden Stand ihres Inhalts sowie jeden Stand der Antwortblasen. */
    private static final class Recorder implements ChatShellModelListener {
        final List<TranscriptEntry> addedActivities = new CopyOnWriteArrayList<TranscriptEntry>();
        final List<String> assistantStates = new CopyOnWriteArrayList<String>();
        private final List<long[]> keys = new CopyOnWriteArrayList<long[]>();
        private final List<String> texts = new CopyOnWriteArrayList<String>();

        List<String> textsOf(TranscriptEntry activity) {
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < keys.size(); i++) {
                if (keys.get(i)[0] == activity.getId()) {
                    result.add(texts.get(i));
                }
            }
            return result;
        }

        private void record(TranscriptEntry entry) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                keys.add(new long[]{entry.getId()});
                texts.add(entry.getText());
            } else if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                assistantStates.add(entry.getText() + entry.getActivity());
            }
        }

        @Override
        public void entryAdded(TranscriptEntry entry) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                Set<Long> known = new LinkedHashSet<Long>();
                for (TranscriptEntry added : addedActivities) {
                    known.add(added.getId());
                }
                if (!known.contains(entry.getId())) {
                    addedActivities.add(entry);
                }
            }
            record(entry);
        }

        @Override
        public void entryUpdated(TranscriptEntry entry) {
            record(entry);
        }

        @Override
        public void entryRemoved(TranscriptEntry entry) {
        }

        @Override
        public void stateChanged() {
        }

        @Override
        public void entriesCleared() {
        }
    }

    /** Ein Modell, das der Reihe nach Werkzeuge aufruft und dann antwortet. */
    private static final class ScriptedChat implements ChatCompletionPort {
        private final java.util.LinkedList<ChatResponse> script = new java.util.LinkedList<ChatResponse>();
        private int calls;

        ScriptedChat call(String tool, String arguments) {
            script.add(new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                    new ChatToolCall("call_" + (++calls), tool, arguments))), ChatFinishReason.TOOL_CALLS,
                    ChatUsage.notReported(), "m"));
            return this;
        }

        ScriptedChat answer(String text) {
            script.add(new ChatResponse(ChatMessage.assistant(text), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "m"));
            return this;
        }

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            return script.isEmpty() ? new ChatResponse(ChatMessage.assistant("?"), ChatFinishReason.STOP,
                    ChatUsage.notReported(), "m") : script.removeFirst();
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            ChatResponse response = complete(request);
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
    }
}
