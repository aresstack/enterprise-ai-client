package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.application.chat.RagSourceFilter;
import com.aresstack.enterpriseai.proasteion.application.composition.LoggingRetrievalObserver;

import com.aresstack.enterpriseai.proasteion.application.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatDraft;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatQuote;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalTriage;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.astu.chat.ChatToolDefinition;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.EDT;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.awaitActivity;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.awaitIdle;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.entries;
import static com.aresstack.enterpriseai.proasteion.exedra.chat.UiTestSupport.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AP22: Die RAG-Anbindung der Shell gegen die Fakes der Stränge A, C, D und E über die echten Use Cases aus
 * {@code application}: Quellen an der Antwort, RAG aus als bisheriger Weg, Stop während des Retrievals,
 * ausgefallene Suche ganz oder teilweise, keine Treffer, abgelehnter Turn.
 */
public class RagChatBindingTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private final ExecutorService workers = UiTestSupport.workers("rag-binding-test");
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
    public void ragOnAttachesSourcesAndSendsTheContextOnlyForThisTurn() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei", " Monate [1].");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(2, entries.size());
        TranscriptEntry answer = entries.get(1);
        assertEquals(TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Drei Monate [1].", answer.getText());
        assertTrue("Quellen an der Antwort", answer.hasSources());
        SourceReference first = answer.getSources().get(0);
        assertEquals(1, first.getNumber());
        assertEquals("Kündigungsfrist", first.getTitle());
        assertEquals(source.idOf("kuendigung").value(), first.getLocation());
        assertTrue(first.getRevision(), first.getRevision().startsWith("Version 1, 2023-11-14"));
        assertTrue(first.getKeywordRank() >= 1);

        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatRole.SYSTEM, sent.get(0).role());
        // The editable system prompt follows the turn context (#126).
        assertTrue(sent.get(0).content().indexOf("\n\nAntworte knapp.") > sent.get(0).content().indexOf("--- KONTEXT ---"));
        assertTrue(sent.get(0).content().contains("--- KONTEXT ---"));
        assertTrue(sent.get(0).content().contains("[1] Kündigungsfrist"));
        assertEquals("Wie lange ist die Kündigungsfrist?", sent.get(sent.size() - 1).content());

        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Kontext nicht in der Historie", 2, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());
        assertEquals("Drei Monate [1].", history.get(1).content());
    }

    /**
     * Restore-Bug: die Quellen einer Antwort sind nach Schließen und Wiederöffnen des Chats wieder an der Antwort
     * (dieselben Titel, Orte, Stände und Ränge), überleben erneutes Speichern, und ein Chat ohne Quellen öffnet weiter.
     */
    @Test
    public void sourcesOfAnAnswerComeBackWhenTheChatIsReopened() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate [1].");
        final Fixture fixture = fixture(index, embeddings);
        final FileChatHistoryStore store = new FileChatHistoryStore(folder.newFolder("chats").toPath());
        final ChatHistoryBinding history = onEdt(new Callable<ChatHistoryBinding>() {
            @Override
            public ChatHistoryBinding call() {
                return new ChatHistoryBinding(store, chat, fixture.binding, fixture.model, "Antworte knapp.",
                        () -> 5L);
            }
        });
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);
        final List<SourceReference> before = entries(fixture.model).get(1).getSources();
        assertFalse("Quellen vor dem Schließen", before.isEmpty());

        // Ein alter Chat ohne Quellen (vor 0.30.0) liegt daneben.
        com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord old =
                new com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord("alt", 1L);
        old.getMessages().add(new com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord(
                com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord.ROLE_USER, "Was ist IAR?", 1L,
                null));
        old.getMessages().add(new com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord(
                com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord.ROLE_ASSISTANT, "Ein Verfahren.",
                2L, null));
        old.setTitle("Was ist IAR?");
        store.save(old);

        // Schließen (anderen Chat öffnen) und wieder öffnen.
        assertTrue(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return history.open("alt");
            }
        }));
        List<TranscriptEntry> oldEntries = entries(fixture.model);
        assertEquals(2, oldEntries.size());
        assertFalse("alter Chat ohne Quellen", oldEntries.get(1).hasSources());
        assertTrue(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return history.open(conversation.value());
            }
        }));

        TranscriptEntry answer = entries(fixture.model).get(1);
        assertTrue("Quellenliste wieder da", answer.hasSources());
        assertSameSources(before, answer.getSources());

        // Erneutes Speichern (eine Notiz löst es aus) verliert keine Quellen.
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.model.addNotice("Hinweis");
            }
        });
        com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord saved = store.load(conversation.value());
        assertEquals(3, saved.getMessages().size());
        assertSameSources(before, ChatHistoryBinding.referencesOf(saved.getMessages().get(1).getSources()));
    }

    /**
     * Ressourcenübersicht und Verweise (Akzeptanztests 5, 7, 8, 9, 12, 13, 14): die Ressourcen des Chats enthalten die
     * Datei und jede verwendete Quelle einmal (über ihre Resource-ID); ein Verweis auf Datei und Quelle legt nichts
     * erneut ab, nennt dem Modell nur die stabilen Kennungen, lässt die sichtbare Nachricht unverändert und kommt nach
     * dem Wiederöffnen wieder an die Nutzernachricht.
     */
    @Test
    public void referencesNameStableIdsAndComeBackAfterReopening() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate [1].");
        port.enqueueAnswer("Weiterhin drei Monate [1].");
        port.enqueueAnswer("Gemeint ist das Quartalsende.");
        final Fixture fixture = fixture(index, embeddings);
        final FileChatHistoryStore store = new FileChatHistoryStore(folder.newFolder("chats").toPath());
        final FileAttachmentStore attachments = new FileAttachmentStore(folder.newFolder("ablage").toPath());
        final java.nio.file.Path file = folder.newFile("IAR-Konzept.txt").toPath();
        java.nio.file.Files.write(file, "IAR ist ein Verfahren.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final ChatHistoryBinding history = onEdt(new Callable<ChatHistoryBinding>() {
            @Override
            public ChatHistoryBinding call() {
                ChatHistoryBinding binding = new ChatHistoryBinding(store, chat, fixture.binding, fixture.model,
                        "Antworte knapp.", () -> 5L);
                fixture.binding.enableTools(new ToolSupport(port, attachments, (name, content) -> "",
                        java.util.Arrays.asList(recordingTool("knowledge_search", new ArrayList<String>(), ""),
                                recordingTool(ChatBookmark.READ_TOOL, new ArrayList<String>(), "SEITE")), false));
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true,
                        Collections.singletonList(file));
                return binding;
            }
        });
        awaitIdle(fixture.model);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Und die Kündigungsfrist im Detail?", true);
            }
        });
        awaitIdle(fixture.model);

        List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource> resources = resources(fixture);
        List<String> files = idsOf(resources, com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource.Kind.FILE);
        List<String> sources = idsOf(resources,
                com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource.Kind.SOURCE);
        assertEquals("die Datei einmal", 1, files.size());
        assertEquals(attachments.list(conversation).get(0).id(), files.get(0));
        assertTrue("die verwendete Quelle ist dabei: " + sources, sources.contains(source.idOf("kuendigung").value()));
        assertEquals("jede Quelle nur einmal", new java.util.LinkedHashSet<String>(sources).size(), sources.size());
        java.util.Set<String> used = new java.util.LinkedHashSet<String>();
        for (TranscriptEntry entry : entries(fixture.model)) {
            for (SourceReference reference : entry.getSources()) {
                used.add(reference.getResourceId());
            }
        }
        assertEquals("alle verwendeten Quellen, jede einmal", new ArrayList<String>(used), sources);
        assertTrue("dieselbe Quelle in zwei Antworten", countSourcesOf(fixture, source.idOf("kuendigung").value()) >= 2);

        final List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource> chosen =
                new ArrayList<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource>();
        for (com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource resource : resources) {
            if (resource.kind() == com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource.Kind.FILE
                    || resource.id().equals(source.idOf("kuendigung").value())) {
                chosen.add(resource);
            }
        }
        assertEquals(2, chosen.size());
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatDraft
                        .of("Was genau ist damit gemeint?", false).withReferences(chosen));
            }
        });
        awaitIdle(fixture.model);

        assertEquals("keine zweite Dateikopie", 1, attachments.list(conversation).size());
        List<ChatMessage> sent = port.lastRequest().messages();
        String system = sent.get(0).content();
        assertTrue(system, system.contains("id = " + files.get(0)));
        assertTrue(system, system.contains("id = " + source.idOf("kuendigung").value()));
        assertTrue(system, system.contains("read_attachment") && system.contains(ChatBookmark.READ_TOOL));
        assertFalse("kein Dateiinhalt im Prompt", system.contains("IAR ist ein Verfahren."));
        assertEquals("sichtbare Nachricht unverändert", "Was genau ist damit gemeint?",
                sent.get(sent.size() - 1).content());
        TranscriptEntry question = entries(fixture.model).get(4);
        assertEquals("Was genau ist damit gemeint?", question.getText());
        assertEquals(chosen, question.getReferences());
        for (ChatMessage message : chat.conversation(conversation).messages()) {
            assertFalse("Kennung nicht im Verlauf", message.content().contains("id = " + files.get(0)));
        }

        com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord saved = store.load(conversation.value());
        assertEquals(2, saved.getMessages().get(4).getReferences().size());
        assertEquals("source", saved.getMessages().get(4).getReferences().get(1).getKind());
        assertTrue(saved.getMessages().get(0).getReferences().isEmpty());
        assertFalse("Resource-ID an der gespeicherten Quelle",
                saved.getMessages().get(1).getSources().get(0).getResourceId().isEmpty());

        com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord other =
                new com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord("anderer", 1L);
        other.getMessages().add(new com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord(
                com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord.ROLE_USER, "Hallo", 1L, null));
        other.setTitle("Hallo");
        store.save(other);
        assertTrue(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return history.open("anderer") && history.open(conversation.value());
            }
        }));
        TranscriptEntry reopened = entries(fixture.model).get(4);
        assertEquals("Was genau ist damit gemeint?", reopened.getText());
        assertEquals("Verweise nach dem Wiederöffnen", chosen, reopened.getReferences());
        assertEquals("Ressourcen nach dem Wiederöffnen", resources, resources(fixture));
    }

    private List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource> resources(final Fixture fixture)
            throws Exception {
        return onEdt(new Callable<List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource>>() {
            @Override
            public List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource> call() {
                return fixture.binding.chatResources();
            }
        });
    }

    private static List<String> idsOf(List<com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource> resources,
                                      com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource.Kind kind) {
        List<String> ids = new ArrayList<String>();
        for (com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource resource : resources) {
            if (resource.kind() == kind) {
                ids.add(resource.id());
            }
        }
        return ids;
    }

    private static int countSourcesOf(Fixture fixture, String resourceId) throws Exception {
        int count = 0;
        for (TranscriptEntry entry : entries(fixture.model)) {
            for (SourceReference reference : entry.getSources()) {
                if (resourceId.equals(reference.getResourceId())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertSameSources(List<SourceReference> expected, List<SourceReference> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            SourceReference a = expected.get(i);
            SourceReference b = actual.get(i);
            assertEquals(a.getNumber(), b.getNumber());
            assertEquals(a.getTitle(), b.getTitle());
            assertEquals(a.getHeading(), b.getHeading());
            assertEquals(a.getLocation(), b.getLocation());
            assertEquals(a.getRevision(), b.getRevision());
            assertEquals(a.getScore(), b.getScore(), 1e-9);
            assertEquals(a.getKeywordRank(), b.getKeywordRank());
            assertEquals(a.getSemanticRank(), b.getSemanticRank());
        }
    }

    @Test
    public void pageBookmarkJoinsTheChatWithoutPageText() throws Exception {
        port.enqueueAnswer("Gelesen");
        final Fixture fixture = fixture(index, embeddings);
        final AiTool knowledgeRead = new AiTool() {
            @Override
            public String name() {
                return ChatBookmark.READ_TOOL;
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.function(ChatBookmark.READ_TOOL, "liest",
                        "{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public String execute(String arguments) {
                return "SEITENTEXT";
            }
        };
        final String bookmark = "confluence://cf.intern/confluence/page/4242001";
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", java.util.Arrays.asList(recordingTool("knowledge_search",
                                new ArrayList<String>(), "TREFFER"), knowledgeRead), false));
                fixture.binding.sendRequested("Was steht da?", false, Collections.<java.nio.file.Path>emptyList(),
                        Collections.singletonList(new ChatPage("Betriebshandbuch", bookmark)));
            }
        });
        awaitIdle(fixture.model);

        TranscriptEntry question = entries(fixture.model).get(0);
        assertEquals(1, question.getPages().size());
        assertEquals(bookmark, question.getPages().get(0).bookmark());
        List<ChatBookmark> pages = onEdt(new Callable<List<ChatBookmark>>() {
            @Override
            public List<ChatBookmark> call() {
                return fixture.binding.bookmarks();
            }
        });
        assertEquals(1, pages.size());
        assertEquals(bookmark, pages.get(0).uri());

        String system = port.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("Betriebshandbuch: " + bookmark));
        assertTrue(system, system.contains(ChatBookmark.READ_TOOL));
        List<String> offered = toolNames(port.lastRequest());
        assertTrue("knowledge_read über /chat/completions angeboten", offered.contains(ChatBookmark.READ_TOOL));
        // Akzeptanztest 3: mit Bookmark sind Suche und Lesen gleichzeitig verfügbar.
        assertTrue("knowledge_search neben knowledge_read: " + offered, offered.contains("knowledge_search"));
        for (ChatMessage message : port.lastRequest().messages()) {
            assertFalse("kein Seitentext im Modellkontext", message.content().contains("SEITENTEXT"));
        }
        for (ChatMessage message : chat.conversation(conversation).messages()) {
            assertFalse("Bookmark nicht in der Historie", message.content().contains(bookmark));
        }
    }

    /**
     * Akzeptanztest 1 (und 5): ohne Bookmark, mit angehakter Quelle bietet der Chat knowledge_search an, das Modell
     * ruft es über die Werkzeug-Schleife; die Anhang-Werkzeuge bleiben daneben bestehen.
     */
    @Test
    public void tickedSourceOffersKnowledgeSearchWithoutBookmark() throws Exception {
        final List<String> searches = new ArrayList<String>();
        final SearchingChat toolChat = new SearchingChat();
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), true);
        final Fixture fixture = fixture(index, embeddings, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(toolChat,
                        new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                        java.util.Arrays.asList(recordingTool("knowledge_search", searches, "id: wiki:sonar"),
                                recordingTool(ChatBookmark.READ_TOOL, new ArrayList<String>(), "SEITE")), false));
                fixture.binding.sendRequested("Suche Seiten zu SonarQube.", true);
            }
        });
        awaitIdle(fixture.model);

        List<String> offered = toolNames(toolChat.requests.get(0));
        assertTrue(offered.toString(), offered.contains("knowledge_search"));
        assertTrue(offered.toString(), offered.contains(ChatBookmark.READ_TOOL));
        assertTrue("Anhang-Werkzeuge bleiben: " + offered, offered.contains("read_attachment")
                && offered.contains("search_attachment"));
        assertEquals(Collections.singletonList("{\"query\":\"SonarQube\"}"), searches);
        assertEquals("Gefunden", entries(fixture.model).get(1).getText());
    }

    /**
     * Diagnose (Chat-Export): die Werkzeugaufrufe einer Antwort kommen mit Name, Argumenten, Erfolg und Ergebnis in
     * den gespeicherten Chat, die Quellen der Antwort ebenso; beides überlebt Öffnen und erneutes Speichern.
     */
    @Test
    public void toolCallsAndSourcesOfAnAnswerAreStoredWithTheChat() throws Exception {
        final SearchingChat toolChat = new SearchingChat();
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), true);
        final Fixture fixture = fixture(index, embeddings, selection);
        final FileChatHistoryStore store = new FileChatHistoryStore(folder.newFolder("chats").toPath());
        onEdt(new Runnable() {
            @Override
            public void run() {
                new ChatHistoryBinding(store, chat, fixture.binding, fixture.model, "Antworte knapp.", () -> 5L);
                fixture.binding.enableTools(new ToolSupport(toolChat,
                        new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                        java.util.Arrays.asList(recordingTool("knowledge_search", new ArrayList<String>(),
                                "id: wiki:sonar"), recordingTool(ChatBookmark.READ_TOOL, new ArrayList<String>(),
                                "SEITE")), false));
                fixture.binding.sendRequested("Suche Seiten zu SonarQube.", true);
            }
        });
        awaitIdle(fixture.model);
        onEdt(new Runnable() {
            @Override
            public void run() {
                // alle UI-Aufträge der Runde sind durch
            }
        });

        com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord record = store.load(conversation.value());
        com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord answer = record.getMessages().get(1);
        assertTrue(answer.isAssistant());
        assertEquals(2, answer.getToolCalls().size());
        com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord search = answer.getToolCalls().get(0);
        assertEquals("die Vorabsuche des Clients steht vorn", ChatHistoryBinding.CLIENT_SEARCH, search.getName());
        assertEquals("{\"query\":\"Suche Seiten zu SonarQube.\"}", search.getArguments());
        assertTrue(search.isSucceeded());
        assertTrue(search.getResult(), search.getResult().endsWith(" Treffer"));
        com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord call = answer.getToolCalls().get(1);
        assertEquals("knowledge_search", call.getName());
        assertEquals("{\"query\":\"SonarQube\"}", call.getArguments());
        assertTrue(call.isSucceeded());
        assertEquals("id: wiki:sonar", call.getResult());
        assertTrue(record.getMessages().get(0).getToolCalls().isEmpty());
    }

    /**
     * Zitat aus einer früheren Antwort: Das Modell bekommt die Passage für genau diesen Turn als System-Anteil, die
     * sichtbare und im Verlauf gespeicherte Nutzernachricht bleibt der eingegebene Text, der nächste Turn ist ohne.
     */
    @Test
    public void quotedPassageGoesToTheModelForThisTurnOnly() throws Exception {
        port.enqueueAnswer("NIZZA ist das Vorsystem für die Übermittlung.");
        port.enqueueAnswer("Gemeint ist das System davor.");
        port.enqueueAnswer("Gern.");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Was ist NIZZA?", false);
            }
        });
        awaitIdle(fixture.model);
        final TranscriptEntry quotedAnswer = entries(fixture.model).get(1);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(ChatDraft.of("Was meinst du hier mit Vorsystem?", false).withQuotes(
                        Collections.singletonList(new ChatQuote(quotedAnswer.getId(),
                                quotedAnswer.getCreatedAtMillis(), "das Vorsystem für die Übermittlung"))));
            }
        });
        awaitIdle(fixture.model);

        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatRole.SYSTEM, sent.get(0).role());
        String system = sent.get(0).content();
        assertTrue(system, system.startsWith("Antworte knapp.\n\n"));
        assertTrue(system, system.contains("folgende Passage aus einer früheren Assistentenantwort"));
        assertTrue(system, system.contains("\u201Edas Vorsystem für die Übermittlung\u201C"));
        assertEquals("sichtbarer Text unverändert an das Modell", "Was meinst du hier mit Vorsystem?",
                sent.get(sent.size() - 1).content());

        TranscriptEntry question = entries(fixture.model).get(2);
        assertEquals("Was meinst du hier mit Vorsystem?", question.getText());
        assertEquals(1, question.getQuotes().size());
        assertEquals("Zitat zeigt auf die richtige Antwort", quotedAnswer.getId(),
                question.getQuotes().get(0).messageId());
        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Was meinst du hier mit Vorsystem?", history.get(2).content());
        for (ChatMessage message : history) {
            assertFalse("kein Zitat-Kontext in der Historie", message.content().contains("früheren Assistentenantwort"));
        }

        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Danke.", false);
            }
        });
        awaitIdle(fixture.model);
        assertEquals("nächster Turn ohne Zitat", "Antworte knapp.", port.lastRequest().messages().get(0).content());
    }

    /**
     * Anschlussfragen zu einem Zitat: Nebenanfrage mit der Passage und dem Umfeld ihrer Antwort, ohne Werkzeuge und
     * ohne Historieneintrag; das Ergebnis kommt auf dem UI-Thread an.
     */
    @Test
    public void quoteFollowUpsUseASideRequestWithTheAnswerAroundThePassage() throws Exception {
        port.enqueueAnswer("NIZZA ist das Vorsystem für die Übermittlung an ZAUF.");
        port.enqueueAnswer("{\"suggestions\": [\"Was bedeutet Vorsystem?\", \"Wofür steht ZAUF?\"]}");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Was ist NIZZA?", false);
            }
        });
        awaitIdle(fixture.model);
        final TranscriptEntry answer = entries(fixture.model).get(1);
        final java.util.concurrent.CompletableFuture<List<String>> received =
                new java.util.concurrent.CompletableFuture<List<String>>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.quoteFollowUpsRequested(new ChatQuote(answer.getId(), answer.getCreatedAtMillis(),
                        "Vorsystem"), suggestions -> {
                            assertTrue("auf dem UI-Thread", javax.swing.SwingUtilities.isEventDispatchThread());
                            received.complete(suggestions);
                        });
            }
        });

        assertEquals(java.util.Arrays.asList("Was bedeutet Vorsystem?", "Wofür steht ZAUF?"),
                received.get(10, java.util.concurrent.TimeUnit.SECONDS));
        ChatRequest side = port.lastRequest();
        assertTrue(side.messages().get(1).content().contains("NIZZA ist das Vorsystem für die Übermittlung"));
        assertTrue(side.tools().isEmpty());
        assertEquals("nicht in der Historie", 2, chat.conversation(conversation).messages().size());
    }

    /** Mehrere Zitate gehen in ihrer Reihenfolge an das Modell, auch auf dem Weg mit Wissenssuche. */
    @Test
    public void multipleQuotesAreSentInOrderAlsoWithRetrieval() throws Exception {
        indexAll();
        port.enqueueAnswer("Beides gilt.");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(ChatDraft.of("Wie hängen die Kündigungsfrist und der Urlaub zusammen?", true).withQuotes(
                        java.util.Arrays.asList(new ChatQuote(7L, 1L, "drei Monate zum Quartalsende"),
                                new ChatQuote(9L, 2L, "dreißig Tage Urlaub"))));
            }
        });
        awaitIdle(fixture.model);

        String system = port.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("--- KONTEXT ---"));
        int first = system.indexOf("1. \u201Edrei Monate zum Quartalsende\u201C");
        int second = system.indexOf("2. \u201Edreißig Tage Urlaub\u201C");
        assertTrue(system, first > 0 && second > first);
        assertTrue(system, system.contains("folgende Passagen aus früheren Assistentenantworten"));
        assertEquals(2, entries(fixture.model).get(0).getQuotes().size());
    }

    /**
     * Ein gesendetes Zitat wird mit der Nutzernachricht gespeichert und zeigt nach dem Wiederöffnen wieder auf die
     * Antwort, aus der es stammt; Chats ohne Zitate behalten ihr bisheriges Format (kein Feld „quotes“).
     */
    @Test
    public void quotesAreStoredWithTheMessageAndRestoredOnTheirAnswer() throws Exception {
        port.enqueueAnswer("NIZZA ist das Vorsystem.");
        port.enqueueAnswer("Das System davor.");
        final java.nio.file.Path chats = folder.newFolder("quote-chats").toPath();
        final FileChatHistoryStore store = new FileChatHistoryStore(chats);
        final Fixture fixture = fixture(index, embeddings);
        final ChatHistoryBinding[] history = new ChatHistoryBinding[1];
        long[] clock = {1000L};
        onEdt(new Runnable() {
            @Override
            public void run() {
                history[0] = new ChatHistoryBinding(store, chat, fixture.binding, fixture.model, "Antworte knapp.",
                        () -> clock[0]++);
                fixture.binding.sendRequested("Was ist NIZZA?", false);
            }
        });
        awaitIdle(fixture.model);
        String before = new String(java.nio.file.Files.readAllBytes(chats.resolve(conversation.value() + ".json")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("ohne Zitat kein neues Feld", before.contains("\"quotes\""));
        final TranscriptEntry quotedAnswer = entries(fixture.model).get(1);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(ChatDraft.of("Was meinst du mit Vorsystem?", false).withQuotes(
                        Collections.singletonList(new ChatQuote(quotedAnswer.getId(),
                                quotedAnswer.getCreatedAtMillis(), "Vorsystem", 13, 22))));
            }
        });
        awaitIdle(fixture.model);
        onEdt(new Runnable() {
            @Override
            public void run() {
                // alle UI-Aufträge der Runde sind durch
            }
        });

        com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord question =
                store.load(conversation.value()).getMessages().get(2);
        assertEquals("Was meinst du mit Vorsystem?", question.getText());
        assertEquals(1, question.getQuotes().size());
        assertEquals("Vorsystem", question.getQuotes().get(0).getText());
        assertEquals(quotedAnswer.getCreatedAtMillis(), question.getQuotes().get(0).getSourceCreatedAt());
        assertEquals(13, question.getQuotes().get(0).getSourceStart());
        assertTrue("Anhänge und Seiten bleiben getrennt", question.getAttachments().isEmpty()
                && question.getBookmarks().isEmpty());

        final List<TranscriptEntry> restored = onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                assertTrue(history[0].open(conversation.value()));
                return fixture.model.getEntries();
            }
        });
        TranscriptEntry restoredQuestion = restored.get(2);
        assertEquals("Was meinst du mit Vorsystem?", restoredQuestion.getText());
        assertEquals(1, restoredQuestion.getQuotes().size());
        ChatQuote quote = restoredQuestion.getQuotes().get(0);
        assertEquals("Vorsystem", quote.selectedText());
        assertEquals("zeigt auf die wiederhergestellte Antwort", restored.get(1).getId(), quote.messageId());
        assertEquals(13, quote.sourceStart());
        assertEquals(22, quote.sourceEnd());
    }

    /**
     * Reiter „Tools“: was der Katalog als aktiv mit Kanal „Chat“ anzeigt, ist genau das, was die Werkzeug-Schleife
     * anbietet. Abgeschaltete Wissens- und Anhang-Werkzeuge fehlen, ohne Neustart wieder eingeschaltet sind sie da.
     */
    @Test
    public void chatOffersExactlyTheToolsTheCatalogShowsAsActive() throws Exception {
        final java.util.Set<String> disabled = new java.util.concurrent.CopyOnWriteArraySet<String>();
        final com.aresstack.enterpriseai.proasteion.application.composition.ToolCatalog catalog =
                new com.aresstack.enterpriseai.proasteion.application.composition.ToolCatalog(
                        name -> !disabled.contains(name));
        final List<AiTool> knowledge = java.util.Arrays.asList(
                recordingTool("knowledge_search", new ArrayList<String>(), ""),
                recordingTool(ChatBookmark.READ_TOOL, new ArrayList<String>(), ""));
        final java.util.EnumSet<com.aresstack.enterpriseai.proasteion.application.composition.ToolChannel> chatOnly =
                java.util.EnumSet.of(com.aresstack.enterpriseai.proasteion.application.composition.ToolChannel.CHAT);
        catalog.registerDefinitions("Wissen", chatOnly, () -> {
            List<ToolDefinition> definitions = new ArrayList<ToolDefinition>();
            for (AiTool tool : knowledge) {
                definitions.add(tool.definition());
            }
            return definitions;
        });
        catalog.registerDefinitions("Anhänge", chatOnly,
                com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTools::definitions);
        final java.util.function.Supplier<List<AiTool>> activeKnowledge = () -> {
            List<AiTool> active = new ArrayList<AiTool>();
            for (AiTool tool : knowledge) {
                if (catalog.isEnabled(tool.name())) {
                    active.add(tool);
                }
            }
            return active;
        };
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), true);
        final Fixture fixture = fixture(index, embeddings, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", activeKnowledge, catalog::isEnabled, true));
            }
        });

        disabled.add("knowledge_search");
        disabled.add("read_attachment");
        List<String> offered = ask(fixture, "Erste Frage");
        assertFalse(offered.toString(), offered.contains("knowledge_search"));
        assertFalse(offered.toString(), offered.contains("read_attachment"));
        assertEquals(shownAsActiveInChat(catalog), new java.util.TreeSet<String>(offered));

        disabled.clear();
        offered = ask(fixture, "Zweite Frage");
        assertTrue(offered.toString(), offered.contains("knowledge_search") && offered.contains("read_attachment"));
        assertEquals(shownAsActiveInChat(catalog), new java.util.TreeSet<String>(offered));
    }

    private List<String> ask(final Fixture fixture, final String question) throws Exception {
        port.enqueueAnswer("ok");
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(question, true);
            }
        });
        awaitIdle(fixture.model);
        return toolNames(port.lastRequest());
    }

    private static java.util.Set<String> shownAsActiveInChat(
            com.aresstack.enterpriseai.proasteion.application.composition.ToolCatalog catalog) {
        java.util.Set<String> names = new java.util.TreeSet<String>();
        for (com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverviewEntry entry
                : new com.aresstack.enterpriseai.proasteion.exedra.settings.CatalogToolOverview(catalog).tools()) {
            if (entry.enabled() && entry.availability().contains("Chat")) {
                names.add(entry.name());
            }
        }
        return names;
    }

    /** Keine Quelle angehakt und keine Seite: normaler Chat, keine Wissens-Werkzeuge. */
    @Test
    public void noTickedSourceAndNoPageOffersNoKnowledgeTools() throws Exception {
        port.enqueueAnswer("Normal");
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), false);
        final Fixture fixture = fixture(index, embeddings, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", Collections.singletonList(recordingTool("knowledge_search",
                                new ArrayList<String>(), "")), false));
                fixture.binding.sendRequested("Hallo", true);
            }
        });
        awaitIdle(fixture.model);
        assertEquals("Normal", entries(fixture.model).get(1).getText());
        assertTrue(toolNames(port.lastRequest()).isEmpty());
    }

    @Test
    public void withoutToolsThePageIsDroppedButTheMessageIsSent() throws Exception {
        port.enqueueAnswer("Trotzdem");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Was steht da?", false, Collections.<java.nio.file.Path>emptyList(),
                        Collections.singletonList(new ChatPage("Betriebshandbuch", "confluence://cf.intern/page/1")));
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(RagChatBinding.PAGES_UNAVAILABLE_NOTICE, entries.get(0).getText());
        assertEquals("Was steht da?", entries.get(1).getText());
        assertEquals("Trotzdem", entries.get(2).getText());
        assertEquals("Was steht da?", port.lastRequest().messages().get(port.lastRequest().messages().size() - 1)
                .content());
    }

    @Test
    public void ragOffIsExactlyThePlainChatPath() throws Exception {
        indexAll();
        int embeddingCalls = embeddings.calls().size();
        port.enqueueAnswer("Hallo");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", false);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(2, entries.size());
        assertFalse(entries.get(1).hasSources());
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
        assertEquals("kein Embedding-Aufruf", embeddingCalls, embeddings.calls().size());
    }

    /** A dictated message tells the model for this turn that it is a speech transcript; the history stays plain. */
    @Test
    public void dictatedMessageCarriesTheSpeechNoteIntoTheRequest() throws Exception {
        port.enqueueAnswer("Gern, das Programm ...");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested(ChatDraft.of("Aklima bitte erstmal das Programm", false)
                        .withOrigin(com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin.SPEECH_TRANSCRIPT));
            }
        });
        awaitIdle(fixture.model);

        assertEquals("Antworte knapp.\n\n"
                        + com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin.SPEECH_TRANSCRIPT.modelNote(),
                port.lastRequest().messages().get(0).content());
        assertEquals("Aklima bitte erstmal das Programm", entries(fixture.model).get(0).getText());
    }

    @Test
    public void stopDuringRetrievalCancelsTheTurnAsSoonAsItExists() throws Exception {
        indexAll();
        final GatedIndex gated = new GatedIndex(index);
        // Die Antwort bleibt offen wie ein echter Stream; ein Backend, das vor dem Stop fertig ist, hat gewonnen.
        port.enqueueHanging("Zu spät");
        final Fixture fixture = fixture(gated, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitActivity(fixture.model, ChatResearchCard.TITLE);
        assertTrue(gated.awaitSearch());
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(fixture.model.canStop());
                fixture.binding.stopRequested();
            }
        });
        gated.release();
        awaitIdle(fixture.model);

        TranscriptEntry answer = entries(fixture.model).get(1);
        assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
        assertEquals("", answer.getActivity());
        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Nutzerfrage bleibt, Antwort nicht", 1, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());
        assertFalse(chat.isBusy(conversation));
        assertTrue("Senden wieder möglich", onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return fixture.model.canSend("weiter");
            }
        }));
    }

    @Test
    public void retrievalFailureBecomesANoticeAndTheAnswerGoesWithoutContext() throws Exception {
        port.enqueueAnswer("Ohne Wissen");
        final Fixture fixture = fixture(new FailingIndex(), embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertEquals(RagChatBinding.RETRIEVAL_FAILED_NOTICE, entries.get(2).getText());
        assertEquals("Ohne Wissen", entries.get(1).getText());
        assertFalse(entries.get(1).hasSources());
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
    }

    @Test
    public void failedSemanticPathBecomesANoticeWhileKeywordSourcesRemain() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate");
        final Fixture fixture = fixture(index, new FailingEmbeddings(embeddings));
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE, entries.get(2).getText());
        assertTrue(entries.get(1).hasSources());
        assertEquals(0, entries.get(1).getSources().get(0).getSemanticRank());
        assertTrue(entries.get(1).getSources().get(0).getKeywordRank() >= 1);
    }

    /**
     * Abnahme 1: „Hi“ bei eingeschalteter Wissenssuche und angehakter Quelle sucht nicht, bettet nichts ein und
     * zeigt keinen Hinweis, selbst wenn die semantische Suche gerade ausfallen würde. Das entscheidet die Vorprüfung
     * durch das Modell, keine Wortliste; auch eine Beschwerde sucht dann nicht.
     */
    @Test
    public void smallTalkWithTickedSourceDoesNotSearchAndShowsNoNotice() throws Exception {
        indexAll();
        final FailingEmbeddings failing = new FailingEmbeddings(embeddings);
        triage = RetrievalTriage.byModel();
        final Fixture fixture = fixture(index, failing);
        for (final String greeting : new String[] {"Hi", "Danke!", "DU HAST EIN MIESES TOOL!"}) {
            port.enqueueAnswer("{\"search\": false, \"query\": \"\"}");
            port.enqueueAnswer("Hallo zurück");
            onEdt(new Runnable() {
                @Override
                public void run() {
                    fixture.binding.sendRequested(greeting, true);
                }
            });
            awaitIdle(fixture.model);
            assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
        }
        assertEquals("kein Embedding, keine Suche", 0, failing.calls.get());
        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals("nur Fragen und Antworten, kein Hinweis", 6, entries.size());
        for (TranscriptEntry entry : entries) {
            assertFalse(entry.toString(), entry.getAuthor() == TranscriptEntry.Author.NOTICE);
            assertFalse(entry.hasSources());
        }
    }

    /**
     * Realfehler: Mit Wissens-Werkzeugen gingen Nachrichten ohne Fragewort am Anfang („an kannst du mir sicherlich
     * sagen was iar ist“) ohne Vorabsuche hinaus, und das Modell antwortete aus eigenem Wissen. Später suchte der
     * Client jede Nachricht wörtlich vorab, auch Gruß und Beschwerde. Jetzt entscheidet die Vorprüfung durch das
     * Modell; knowledge_search wird weiterhin angeboten, mit den Werkzeugregeln des Clients.
     */
    @Test
    public void withKnowledgeToolsTheTriageDecidesWhatIsSearchedAhead() throws Exception {
        indexAll();
        final FailingEmbeddings failing = new FailingEmbeddings(embeddings);
        triage = RetrievalTriage.byModel();
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), true);
        final Fixture fixture = fixture(index, failing, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", Collections.singletonList(recordingTool("knowledge_search",
                                new ArrayList<String>(), "")), false));
            }
        });
        port.enqueueAnswer("{\"search\": false}");
        port.enqueueAnswer("Hallo");
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Hallo, was kannst du denn so alles für mich tun?", true);
            }
        });
        awaitIdle(fixture.model);
        assertTrue(toolNames(port.lastRequest()).contains("knowledge_search"));
        assertTrue(port.lastRequest().messages().get(0).content().contains("[Werkzeugregeln des Clients]"));
        assertEquals("Small Talk sucht nicht", 0, failing.calls.get());

        int expected = 0;
        for (final String message : new String[] {"an kannst du mir sicherlich sagen was iar ist",
                "Wie lange ist die Kündigungsfrist?"}) {
            port.enqueueAnswer("{\"search\": true, \"query\": \"" + message + "\"}");
            port.enqueueAnswer("Antwort");
            onEdt(new Runnable() {
                @Override
                public void run() {
                    fixture.binding.sendRequested(message, true);
                }
            });
            awaitIdle(fixture.model);
            assertTrue(toolNames(port.lastRequest()).contains("knowledge_search"));
            assertEquals("vorab gesucht: " + message, ++expected, failing.calls.get());
        }
    }

    /**
     * Realfehler: „Kannst Du auch Recherchieren? Hast Du MCP Tools? Welche?“ löste die Vorabsuche aus, und Seiten
     * über Recherche-Rollen und Werkzeuge eines Fachsystems landeten als Kontext im Prompt. Eine Frage nach dem
     * Assistenten selbst sucht nicht und bekommt stattdessen genau die Werkzeuge dieses Turns genannt; abgeschaltete
     * fehlen.
     */
    @Test
    public void capabilityQuestionDoesNotSearchAndNamesOnlyTheOfferedTools() throws Exception {
        final CountingEmbeddings counting = new CountingEmbeddings(embeddings);
        final InMemoryKnowledgeSource wiki = indexCapabilityTraps(counting);
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(wiki.sourceId(), true);
        final Fixture fixture = fixture(index, counting, selection);
        final List<String> searches = new ArrayList<String>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", () -> java.util.Arrays.asList(
                                recordingTool("knowledge_search", searches, ""),
                                recordingTool(ChatBookmark.READ_TOOL, new ArrayList<String>(), "")),
                        name -> !"search_attachment".equals(name), false));
            }
        });
        counting.calls.set(0);
        // Realfehler: das Modell nannte die Werkzeuge und dazu „GPT-4 mit Wissensstand bis Juni 2024“. Die Antwort
        // schreibt jetzt der Client; ein Modellaufruf, der ein Selbstbild liefern könnte, findet nicht statt.
        port.enqueueAnswer("Ich bin GPT-4 mit Wissensstand Juni 2024.");
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Kannst Du auch Recherchieren? Hast Du MCP Tools? Welche?", true);
            }
        });
        awaitIdle(fixture.model);

        assertEquals("keine Vorabsuche", 0, counting.calls.get());
        assertTrue(searches.isEmpty());
        assertTrue("kein Modellaufruf", port.requests().isEmpty());
        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals("keine Hinweiszeile", 2, entries.size());
        assertFalse("keine Quellen", entries.get(1).hasSources());
        String answer = entries.get(1).getText();
        assertFalse(answer, answer.contains("GPT"));
        assertFalse(answer, answer.contains("IAR"));
        for (String tool : new String[] {"read_attachment", "knowledge_search", ChatBookmark.READ_TOOL}) {
            assertTrue(answer, answer.contains("- **" + tool + "**"));
        }
        assertFalse("abgeschaltet: " + answer, answer.contains("search_attachment"));
        assertTrue(answer, answer.contains("im Composer ist keines gewählt"));
    }

    /** Ein im Composer gewähltes Modell nennt die Fähigkeitsantwort; ein anderes Modell kennt sie nicht. */
    @Test
    public void capabilityAnswerNamesOnlyTheModelChosenInTheComposer() throws Exception {
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.modelChanged("modell-a");
                fixture.binding.sendRequested("Welches Modell verwendest du?", true);
            }
        });
        awaitIdle(fixture.model);

        assertTrue("kein Modellaufruf", port.requests().isEmpty());
        String answer = entries(fixture.model).get(1).getText();
        assertTrue(answer, answer.contains("**modell-a**"));
        assertTrue(answer, answer.contains("keine Werkzeuge"));
    }

    /** Ohne Werkzeuge sagt der Fähigkeits-Kontext genau das, statt die Wissensbasis zu fragen. */
    @Test
    public void capabilityQuestionWithoutToolsSaysSoAndDoesNotSearch() throws Exception {
        final CountingEmbeddings counting = new CountingEmbeddings(embeddings);
        indexCapabilityTraps(counting);
        final Fixture fixture = fixture(index, counting);
        counting.calls.set(0);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Welche Tools hast du?", true);
            }
        });
        awaitIdle(fixture.model);

        assertEquals(0, counting.calls.get());
        assertTrue("kein Modellaufruf", port.requests().isEmpty());
        String answer = entries(fixture.model).get(1).getText();
        assertFalse(answer, answer.contains("IAR"));
        assertTrue(answer, answer.contains("keine Werkzeuge"));
        assertFalse(entries(fixture.model).get(1).hasSources());
    }

    /** Eine echte Wissensfrage zum selben Fachsystem sucht weiterhin vorab und bekommt die Seiten als Quellen. */
    @Test
    public void knowledgeQuestionAboutTheSameSystemStillSearches() throws Exception {
        final CountingEmbeddings counting = new CountingEmbeddings(embeddings);
        final InMemoryKnowledgeSource wiki = indexCapabilityTraps(counting);
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(wiki.sourceId(), true);
        final Fixture fixture = fixture(index, counting, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(port, new FileAttachmentStore(folder.getRoot().toPath()),
                        (name, content) -> "", Collections.singletonList(recordingTool("knowledge_search",
                                new ArrayList<String>(), "")), false));
            }
        });
        counting.calls.set(0);
        port.enqueueAnswer("IAR ist ein Fachsystem [1].");
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Was ist IAR?", true);
            }
        });
        awaitIdle(fixture.model);

        assertTrue("vorab gesucht", counting.calls.get() > 0);
        assertTrue(entries(fixture.model).get(1).hasSources());
        String system = port.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("--- KONTEXT ---"));
    }

    /** Seiten, deren Begriffe einer Fähigkeitsfrage ähneln (Recherche, Tool, Rolle), aber Fachinhalt sind. */
    private InMemoryKnowledgeSource indexCapabilityTraps(EmbeddingPort embeddingPort) {
        InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("fachwiki")
                .add("rollen", "IAR Rollen", "IAR Recherche: die Rolle Recherche darf Vorgänge recherchieren. "
                        + "Tools und MCP Tools der Fachabteilung.")
                .add("werkzeuge", "IAR Werkzeuge", "Tool für IAR: Hauptfunktion Recherche, Werkzeuge zum Testen.");
        new IndexKnowledgeUseCase(index, embeddingPort, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .indexSource(wiki, SourceScope.of("rollen", "werkzeuge"), null);
        return wiki;
    }

    @Test
    public void deselectedSourcesAreNotSearched() throws Exception {
        indexAll();
        port.enqueueAnswer("Weiß ich nicht");
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), false);
        selection.register(SourceAddress.of("andere"), true);
        final Fixture fixture = fixture(index, embeddings, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertFalse("keine Treffer aus der abgewählten Quelle", entries.get(1).hasSources());
        assertFalse(port.lastRequest().messages().get(0).content().contains("Kündigungsfrist"));
    }

    @Test
    public void noTickedSourceAnswersAsPlainChatWithoutNotice() throws Exception {
        indexAll();
        int embeddingCalls = embeddings.calls().size();
        port.enqueueAnswer("Ohne Kontext");
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        selection.register(source.sourceId(), false);
        final Fixture fixture = fixture(index, embeddings, selection);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<String> texts = new ArrayList<String>();
        for (TranscriptEntry entry : entries(fixture.model)) {
            texts.add(entry.getText());
        }
        assertEquals("nur Frage und Antwort, kein Hinweis", 2, texts.size());
        assertTrue(texts.toString(), texts.contains("Ohne Kontext"));
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
        assertEquals("kein Embedding-Aufruf", embeddingCalls, embeddings.calls().size());
    }

    @Test
    public void noHitsBecomeANotice() throws Exception {
        port.enqueueAnswer("Weiß ich nicht");
        final Fixture fixture = fixture(index, embeddings); // leerer Index
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(RagChatBinding.NO_SOURCES_NOTICE, entries.get(2).getText());
        assertFalse(entries.get(1).hasSources());
    }

    @Test
    public void rejectedTurnBecomesAnErrorBubbleWithoutDetails() throws Exception {
        indexAll();
        port.enqueueHanging("beschäftigt");
        chat.send(conversation, "direkt", new ChatTurnListener() {
            @Override
            public void onDelta(String text) {
            }

            @Override
            public void onCompleted(ChatResponse response) {
            }

            @Override
            public void onFailed(ChatCompletionException error) {
            }

            @Override
            public void onCancelled() {
            }
        });
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);
        TranscriptEntry answer = entries(fixture.model).get(1);
        assertEquals(TranscriptEntry.State.FAILED, answer.getState());
        assertEquals(RagChatBinding.SEND_REJECTED, answer.getFailureMessage());
        chat.cancel(conversation);
    }

    @Test
    public void revisionAndLocationAreHumanReadable() throws Exception {
        RagChatBinding binding = fixture(index, embeddings).binding;
        assertEquals("", binding.revision(KnowledgeRevision.unknown()));
        assertEquals("Version 42", binding.revision(KnowledgeRevision.version("42")));
        assertEquals("2026-10-01 08:00", binding.revision(KnowledgeRevision.modifiedAt(
                Instant.parse("2026-10-01T08:00:00Z"))));
        assertEquals("Version 7, 2026-10-01 10:30", binding.revision(KnowledgeRevision.of(
                Instant.parse("2026-10-01T10:30:00Z"), "7")));
    }

    @Test
    public void lateSourcesOfAFastAnswerLeaveTheNextRetrievalUntouched() throws Exception {
        // Regression (Copilot-Finding): Antwort A ist fertig, bevor attach(A) den UI-Thread erreicht; Frage B
        // sucht schon. attach(A) darf den Suchzustand von B nicht löschen, sonst geht ein Stop während B verloren.
        indexAll();
        final ManualExecutor ui = new ManualExecutor();
        final ManualExecutor work = new ManualExecutor();
        final CompletionAwaitingPort gate = new CompletionAwaitingPort(port);
        ChatService gatedChat = new ChatService(gate);
        ChatConversationId id = gatedChat.openConversation("Antworte knapp.");
        RagChatUseCase rag = new RagChatUseCase(gatedChat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null));
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        RagChatBinding binding = new RagChatBinding(rag, id, model, ui, work, UTC);

        port.enqueueAnswer("Schnell.");
        gate.awaitCompletionOnNextStream();
        binding.sendRequested("Frage A", true);
        work.runAll(); // Suche A und Turn A; der Fake ist fertig, bevor send() zurückkehrt
        ui.runWhile(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return model.isStreaming(); // bis die Antwort A fertig ist; ihre Recherchekarte ist dann weg
            }
        });
        List<TranscriptEntry> entries = model.getEntries();
        assertEquals(TranscriptEntry.State.COMPLETE, entries.get(1).getState());
        assertFalse("Quellen von A sind noch unterwegs", entries.get(1).hasSources());
        assertEquals("attach(A) wartet noch auf dem UI-Thread", 1, ui.pending());

        port.enqueueHanging("Langsam");
        binding.sendRequested("Frage B", true); // B beginnt die Suche
        ui.runAll();                             // attach(A): Quellen an A, Zustand von B unberührt
        entries = model.getEntries();
        // The research card of B appears with its first search step, which has not run yet.
        assertEquals("Frage, Antwort, Frage B, Antwort B", 4, entries.size());
        assertTrue("späte Quellen landen an der fertigen Antwort", entries.get(1).hasSources());
        assertEquals(TranscriptEntry.State.STREAMING, entries.get(3).getState());

        binding.stopRequested(); // während der Suche von B, bevor sie lief: ihre Suchschritte entfallen
        work.runAll();           // Turn B (hängt)
        ui.runAll();             // attach(B) löst den Stop ein
        ui.runUntil("Antwort B abgebrochen", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !model.isStreaming();
            }
        });
        entries = model.getEntries();
        assertEquals("Frage, Antwort, Frage B, Antwort B; kein Hinweis „ohne Treffer“", 4, entries.size());
        assertEquals(TranscriptEntry.State.CANCELLED, entries.get(3).getState());
        assertFalse("die übersprungene Suche bringt keine Quellen", entries.get(3).hasSources());
        assertTrue(entries.get(1).hasSources());
        assertTrue(model.canSend("Nochmal"));
    }

    @Test
    public void rejectedWorkExecutorFailsTheAnswerInsteadOfLeavingItOpen() throws Exception {
        indexAll();
        Executor rejecting = new Executor() {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("shut down");
            }
        };
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final RagChatBinding binding = new RagChatBinding(new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null)),
                conversation, model, EDT, rejecting, UTC);
        onEdt(new Runnable() {
            @Override
            public void run() {
                binding.sendRequested("Frage", true);
                List<TranscriptEntry> entries = model.getEntries();
                assertEquals(2, entries.size());
                assertEquals(TranscriptEntry.State.FAILED, entries.get(1).getState());
                assertEquals(RagChatBinding.SEND_REJECTED, entries.get(1).getFailureMessage());
                assertTrue("die Shell ist wieder frei", model.canSend("Nochmal"));
                binding.stopRequested(); // nichts mehr abzubrechen, kein Fehler
            }
        });
        assertFalse(chat.isBusy(conversation));
    }

    @Test
    public void failedSemanticPathWithoutKeywordHitsNamesBothInTheNotice() throws Exception {
        // Leerer Index: der Volltext findet nichts, die Embeddings fallen aus – beides muss im Hinweis stehen.
        port.enqueueAnswer("Ohne Kontext.");
        final Fixture fixture = fixture(index, new FailingEmbeddings(embeddings));
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);
        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertFalse(entries.get(1).hasSources());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE + " " + RagChatBinding.NO_SOURCES_NOTICE,
                entries.get(2).getText());
    }

    private static AiTool recordingTool(final String name, final List<String> calls, final String result) {
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
                synchronized (calls) {
                    calls.add(arguments);
                }
                return result;
            }
        };
    }

    private static List<String> toolNames(ChatRequest request) {
        List<String> names = new ArrayList<String>();
        if (request.tools() != null) {
            for (ChatToolDefinition tool : request.tools()) {
                names.add(tool.name());
            }
        }
        return names;
    }

    /** Das Modell ruft zuerst knowledge_search(query="SonarQube") und antwortet dann. */
    private static final class SearchingChat implements ChatCompletionPort {
        final List<ChatRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<ChatRequest>();

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                        new com.aresstack.enterpriseai.astu.chat.ChatToolCall("c1", "knowledge_search",
                                "{\"query\":\"SonarQube\"}"))),
                        com.aresstack.enterpriseai.astu.chat.ChatFinishReason.TOOL_CALLS,
                        com.aresstack.enterpriseai.astu.chat.ChatUsage.notReported(), "m");
            }
            return new ChatResponse(ChatMessage.assistant("Gefunden"),
                    com.aresstack.enterpriseai.astu.chat.ChatFinishReason.STOP,
                    com.aresstack.enterpriseai.astu.chat.ChatUsage.notReported(), "m");
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

    @Test
    public void readPageCountsAsContextWhenTheSemanticPathFails() throws Exception {
        // Akzeptanztest 4: RAG findet nichts (Volltext leer, Embeddings fallen aus), knowledge_read liest die Seite.
        final RagChatUseCase rag = new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index,
                new FailingEmbeddings(embeddings), space, null).withObserver(new LoggingRetrievalObserver()),
                new PromptContextAssembler(null));
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                return new Fixture(rag);
            }
        });
        final ScriptedToolChat toolChat = new ScriptedToolChat().readPage().answer("Laut Seite: drei Monate.");
        final List<String> log = captureLog();
        sendWithPage(fixture, toolChat, pageTool("SEITENTEXT Kündigungsfrist drei Monate"));
        awaitEntries(fixture.model, 3);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals("Laut Seite: drei Monate.", entries.get(1).getText());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_PAGE_READ_NOTICE, entries.get(2).getText());
        assertFalse(entries.get(2).getText().contains("ohne Kontext"));
        assertFalse(entries.get(2).getText().contains(RagChatBinding.NO_SOURCES_NOTICE));
        assertTrue("Seitentext ging an das Modell", toolChat.toolResults().contains("SEITENTEXT"));
        // Akzeptanztest 6: die Ursache steht im technischen Log, die Frage nicht.
        String line = join(log);
        assertTrue(line, line.contains("SEMANTIC") && line.contains("UNAVAILABLE")
                && line.contains("embedding service down"));
        assertFalse(line, line.contains(QUESTION));
    }

    @Test
    public void failedPageReadAndNoRagHitsMeanNoContext() throws Exception {
        // Akzeptanztest 5: knowledge_read scheitert ebenfalls und RAG liefert nichts.
        final Fixture fixture = fixture(index, new FailingEmbeddings(embeddings));
        final ScriptedToolChat toolChat = new ScriptedToolChat().readPage().answer("Weiß ich nicht.");
        sendWithPage(fixture, toolChat, pageTool(null));
        awaitEntries(fixture.model, 3);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE + " " + RagChatBinding.NO_SOURCES_NOTICE,
                entries.get(2).getText());
        assertTrue(toolChat.toolResults().startsWith("Fehler:"));
    }

    @Test
    public void readPageWithoutRagFailureNeedsNoNotice() throws Exception {
        // RAG lief vollständig ohne Treffer, die Seite wurde gelesen: kein „Keine passenden Abschnitte“.
        final Fixture fixture = fixture(index, embeddings);
        final ScriptedToolChat toolChat = new ScriptedToolChat().readPage().answer("Laut Seite: drei Monate.");
        sendWithPage(fixture, toolChat, pageTool("SEITENTEXT"));
        awaitIdle(fixture.model);
        Thread.sleep(200); // ein später Hinweis hätte jetzt den UI-Thread erreicht
        awaitIdle(fixture.model);

        assertEquals("nur Frage und Antwort", 2, entries(fixture.model).size());
    }

    private static final String QUESTION = "Wie lange ist die Kündigungsfrist laut Seite?";

    private void sendWithPage(final Fixture fixture, final ScriptedToolChat toolChat, final AiTool knowledgeRead)
            throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.enableTools(new ToolSupport(toolChat, new FileAttachmentStore(folder.getRoot()
                        .toPath()), (name, content) -> "", Collections.singletonList(knowledgeRead), false));
                fixture.binding.sendRequested(QUESTION, true, Collections.<java.nio.file.Path>emptyList(),
                        Collections.singletonList(new ChatPage("Betriebshandbuch",
                                "confluence://cf.intern/confluence/page/4242001")));
            }
        });
    }

    /** {@code knowledge_read}, das {@code text} liefert oder bei {@code null} scheitert. */
    private static AiTool pageTool(final String text) {
        return new AiTool() {
            @Override
            public String name() {
                return ChatBookmark.READ_TOOL;
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.function(ChatBookmark.READ_TOOL, "liest",
                        "{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public String execute(String arguments) {
                if (text == null) {
                    throw new IllegalStateException("Quelle nicht erreichbar");
                }
                return text;
            }
        };
    }

    private static void awaitEntries(final ChatShellModel model, final int count) throws Exception {
        UiTestSupport.await(count + " Einträge", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return model.getEntries().size() >= count && !model.isStreaming();
            }
        });
    }

    private static List<String> captureLog() {
        final List<String> lines = Collections.synchronizedList(new ArrayList<String>());
        java.util.logging.Logger.getLogger(LoggingRetrievalObserver.class.getName()).addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return lines;
    }

    private static String join(List<String> lines) {
        synchronized (lines) {
            return String.join("\n", lines);
        }
    }

    /** Das Modell der Werkzeug-Schleife: erst ein Aufruf von knowledge_read, dann die Antwort. */
    private static final class ScriptedToolChat implements ChatCompletionPort {
        private final java.util.LinkedList<ChatResponse> script = new java.util.LinkedList<ChatResponse>();
        private volatile String toolResults = "";

        ScriptedToolChat readPage() {
            script.add(new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                    new com.aresstack.enterpriseai.astu.chat.ChatToolCall("call_1", ChatBookmark.READ_TOOL,
                            "{\"id\":\"confluence://cf.intern/confluence/page/4242001\"}"))),
                    com.aresstack.enterpriseai.astu.chat.ChatFinishReason.TOOL_CALLS,
                    com.aresstack.enterpriseai.astu.chat.ChatUsage.notReported(), "m"));
            return this;
        }

        ScriptedToolChat answer(String text) {
            script.add(new ChatResponse(ChatMessage.assistant(text),
                    com.aresstack.enterpriseai.astu.chat.ChatFinishReason.STOP,
                    com.aresstack.enterpriseai.astu.chat.ChatUsage.notReported(), "m"));
            return this;
        }

        String toolResults() {
            return toolResults;
        }

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            StringBuilder results = new StringBuilder();
            for (ChatMessage message : request.messages()) {
                if (message.role() == ChatRole.TOOL) {
                    results.append(message.content());
                }
            }
            toolResults = results.toString();
            return script.removeFirst();
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

    private void indexAll() {
        new IndexKnowledgeUseCase(index, embeddings, space, new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .indexSource(source, SourceScope.of("urlaub", "kuendigung"), null);
    }

    /** Vorprüfung der Fixtures dieses Tests; ohne sie wird jede Nachricht mit angehakter Quelle wörtlich gesucht. */
    private RetrievalTriage triage = RetrievalTriage.off();

    private Fixture fixture(KnowledgeIndexPort indexPort, EmbeddingPort embeddingPort) throws Exception {
        return fixture(indexPort, embeddingPort, RagSourceFilter.unrestricted());
    }

    private Fixture fixture(KnowledgeIndexPort indexPort, EmbeddingPort embeddingPort, final RagSourceFilter filter)
            throws Exception {
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(indexPort, embeddingPort, space, null),
                new PromptContextAssembler(null), null, null, null, triage);
        return onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                return new Fixture(rag, filter);
            }
        });
    }

    private final class Fixture {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final RagChatBinding binding;

        Fixture(RagChatUseCase rag) {
            this(rag, RagSourceFilter.unrestricted());
        }

        Fixture(RagChatUseCase rag, RagSourceFilter filter) {
            binding = new RagChatBinding(rag, conversation, model, EDT, workers, UTC, filter);
        }
    }

    /**
     * Lässt {@code stream()} auf Wunsch erst zurückkehren, wenn der Fake die Antwort abgeschlossen hat: so liegt
     * der Abschluss vor den Quellen auf dem UI-Thread, wie bei einem Backend, das schneller ist als der Umweg.
     */
    private static final class CompletionAwaitingPort implements ChatCompletionPort {
        private final ChatCompletionPort delegate;
        private volatile boolean awaitNext;

        CompletionAwaitingPort(ChatCompletionPort delegate) {
            this.delegate = delegate;
        }

        void awaitCompletionOnNextStream() {
            awaitNext = true;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return delegate.complete(request);
        }

        @Override
        public ChatTask stream(ChatRequest request, final ChatStreamListener listener) {
            if (!awaitNext) {
                return delegate.stream(request, listener);
            }
            awaitNext = false;
            final CountDownLatch finished = new CountDownLatch(1);
            ChatTask task = delegate.stream(request, new ChatStreamListener() {
                @Override
                public void onStart() {
                    listener.onStart();
                }

                @Override
                public void onDelta(String text) {
                    listener.onDelta(text);
                }

                @Override
                public void onComplete(ChatResponse response) {
                    listener.onComplete(response);
                    finished.countDown();
                }

                @Override
                public void onError(ChatCompletionException error) {
                    listener.onError(error);
                    finished.countDown();
                }

                @Override
                public void onCancelled() {
                    listener.onCancelled();
                    finished.countDown();
                }
            });
            try {
                assertTrue("Fake-Antwort abgeschlossen",
                        finished.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return task;
        }
    }

    /** Hält jede Suche an, bis der Test sie freigibt: simuliert ein langsames Retrieval. */
    static final class GatedIndex implements KnowledgeIndexPort {
        private final KnowledgeIndexPort delegate;
        private final CountDownLatch searching = new CountDownLatch(1);
        private final CountDownLatch gate = new CountDownLatch(1);

        GatedIndex(KnowledgeIndexPort delegate) {
            this.delegate = delegate;
        }

        boolean awaitSearch() throws InterruptedException {
            return searching.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        void release() {
            gate.countDown();
        }

        private void hold() {
            searching.countDown();
            try {
                gate.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            delegate.index(entries);
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            delegate.replace(space, resourceId, entries);
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            hold();
            return delegate.keywordSearch(query);
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            hold();
            return delegate.semanticSearch(query);
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            delegate.remove(resourceId);
        }

        @Override
        public void removeSource(SourceAddress sourceId) {
            delegate.removeSource(sourceId);
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            delegate.rebuild(entries);
        }

        @Override
        public Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space, SourceAddress sourceId) {
            return delegate.resourceIds(space, sourceId);
        }

        @Override
        public Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId) {
            return delegate.revisionOf(space, resourceId);
        }
    }

    /** Beide Suchpfade fallen aus (z. B. beschädigtes Indexverzeichnis). */
    static final class FailingIndex implements KnowledgeIndexPort {
        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void removeSource(SourceAddress sourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space, SourceAddress sourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }
    }

    /** Der Embedding-Dienst ist nicht erreichbar: die semantische Suche fällt aus, die Volltextsuche bleibt. */
    /** Zählt die Einbettungsaufrufe: jede Vorabsuche bettet die Frage ein. */
    private static final class CountingEmbeddings implements EmbeddingPort {
        private final EmbeddingPort delegate;
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        CountingEmbeddings(EmbeddingPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return delegate.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            calls.incrementAndGet();
            return delegate.embed(texts);
        }
    }

    private static final class FailingEmbeddings implements EmbeddingPort {
        private final EmbeddingPort identityOf;
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        FailingEmbeddings(EmbeddingPort identityOf) {
            this.identityOf = identityOf;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return identityOf.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            calls.incrementAndGet();
            throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "embedding service down");
        }
    }

    @SuppressWarnings("unused")
    private static void assertNoSources(TranscriptEntry entry) {
        assertNull(entry.hasSources() ? entry.getSources() : null);
    }
}
