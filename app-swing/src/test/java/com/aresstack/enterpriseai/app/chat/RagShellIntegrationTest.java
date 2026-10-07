package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeChatCompletionsServer;
import com.aresstack.enterpriseai.app.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusBar;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.app.ui.chat.SourceListPanel;
import com.aresstack.enterpriseai.app.ui.chat.SourceReference;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aresstack.enterpriseai.app.chat.UiTestSupport.EDT;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.TIMEOUT_MILLIS;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.await;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.awaitIdle;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.awaitText;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.entries;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * AP22, Pflicht laut Auftrag: der vollständige lokale Integrationstest der RAG-Shell, headless ohne Fenster.
 *
 * <p>Echte Kette Shell ({@link ChatShellPanel}, Eingabezeile, RAG-Schalter) → {@link RagChatBinding} und
 * {@link KnowledgeIndexingBinding} → Use Cases aus {@code application} → echte Adapter
 * {@link OpenAiCompatibleChatAdapter} (AP3) und {@link OpenAiCompatibleEmbeddingAdapter} (AP6) über HTTP gegen
 * lokale Fake-Server für {@code /chat/completions} und {@code /embeddings} → {@link LuceneKnowledgeIndex} (AP9) in
 * einem temporären Verzeichnis, {@code InMemoryKnowledgeSource} (AP11) als Quelle.
 *
 * <p>Ablauf: Indexierung mit Fortschritt in der Statuszeile, Frage mit RAG, Antwort mit Quellen; dazu RAG aus,
 * Stop, Fehler des Chat-Dienstes und ausgefallener Embedding-Dienst. Das Bearer-Token geht an beide Server und
 * taucht nirgends in der Oberfläche auf.
 */
public class RagShellIntegrationTest {

    private static final String TOKEN = "test-token-SECRET-0815";
    private static final String SYSTEM_PROMPT = "Antworte knapp und nenne die Quellen in eckigen Klammern.";
    private static final int DIMENSION = 64;

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private final ExecutorService workers = UiTestSupport.workers("rag-shell-integration");
    private FakeChatCompletionsServer chatServer;
    private FakeEmbeddingsServer embeddingsServer;
    private LuceneKnowledgeIndex index;
    private ChatService chat;
    private ChatConversationId conversation;
    private Shell shell;

    private final InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("handbuch")
            .add("urlaub", "Urlaubsregelung", "Mitarbeiterinnen und Mitarbeiter haben dreißig Tage Urlaub im "
                    + "Kalenderjahr. Resturlaub verfällt am 31. März des Folgejahres, wenn er nicht beantragt wurde.")
            .add("kuendigung", "Kündigungsfrist", "Die Kündigungsfrist beträgt drei Monate zum Quartalsende. Die "
                    + "Frist gilt für beide Seiten; eine Kündigung muss schriftlich erfolgen.")
            .add("gleitzeit", "Gleitzeit", "Die Kernarbeitszeit liegt zwischen neun und fünfzehn Uhr. Außerhalb "
                    + "der Kernarbeitszeit kann die Arbeitszeit frei gestaltet werden.");

    @Before
    public void setUp() throws Exception {
        chatServer = new FakeChatCompletionsServer();
        embeddingsServer = new FakeEmbeddingsServer(DIMENSION);

        OpenAiCompatibleChatAdapter chatAdapter = new OpenAiCompatibleChatAdapter(
                OpenAiCompatibleChatConfig.builder(chatServer.baseUrl(), "openai/gpt-oss-120b")
                        .bearerToken(OpenAiCompatibleChatConfig.TokenSource.fixed(TOKEN))
                        .build());
        OpenAiCompatibleEmbeddingAdapter embeddingAdapter = new OpenAiCompatibleEmbeddingAdapter(
                OpenAiCompatibleEmbeddingConfiguration.builder(embeddingsServer.baseUrl(),
                        "danielheinz/e5-base-sts-en-de", DIMENSION).build(),
                new BearerTokenSource() {
                    @Override
                    public char[] bearerToken() {
                        return TOKEN.toCharArray();
                    }
                });
        EmbeddingModelIdentity space = embeddingAdapter.modelIdentity();
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());

        chat = new ChatService(chatAdapter);
        conversation = chat.openConversation(SYSTEM_PROMPT);
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, embeddingAdapter, space, null),
                new PromptContextAssembler(null));
        final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddingAdapter, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        shell = onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                return new Shell(rag, indexing);
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        workers.shutdownNow();
        chatServer.close();
        embeddingsServer.close();
        index.close();
    }

    @Test
    public void indexThenAskWithRagThenWithoutStopAndFail() throws Exception {
        // 1. Indexierung: Fortschritt in der Statuszeile, Chunks gehen einzeln an /embeddings.
        IndexingReport report = indexHandbuch();
        assertTrue(report.toString(), report.isComplete());
        assertEquals(3, report.outcomes().size());
        int chunks = report.chunkCount();
        assertTrue(chunks >= 3);
        assertEquals("ein Request je Chunk (SINGLE_STRING)", chunks, embeddingsServer.requestCount());
        assertEquals("Bearer " + TOKEN, embeddingsServer.requests().get(0).authorization());
        String status = onEdt(new Callable<String>() {
            @Override
            public String call() {
                return shell.panel.statusBar().getText();
            }
        });
        assertTrue(status, status.startsWith("Wissensbasis: 3 Seiten, " + chunks + " Abschnitte (Stand "));
        assertTrue(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return shell.panel.statusBar().isVisible() && !shell.panel.statusBar().cancelButton().isVisible();
            }
        }));

        // 2. Frage mit RAG über die Eingabezeile: Kontext im Request, Quellen unter der Antwort.
        chatServer.answerWith("Die Kündigungsfrist beträgt ", "drei Monate zum Quartalsende [1].");
        ask("Wie lange ist die Kündigungsfrist?", true);
        awaitIdle(shell.model);

        List<TranscriptEntry> entries = entries(shell.model);
        assertEquals(2, entries.size());
        TranscriptEntry answer = entries.get(1);
        assertEquals(TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Die Kündigungsfrist beträgt drei Monate zum Quartalsende [1].", answer.getText());
        assertTrue("Quellen an der Antwort", answer.hasSources());
        SourceReference first = answer.getSources().get(0);
        assertEquals(1, first.getNumber());
        assertEquals("Kündigungsfrist", first.getTitle());
        assertEquals(source.idOf("kuendigung").value(), first.getLocation());
        assertTrue(first.getRevision(), first.getRevision().startsWith("Version 1, "));
        assertTrue("Volltext und Semantik finden den Abschnitt", first.getKeywordRank() >= 1
                && first.getSemanticRank() >= 1);
        final long answerId = answer.getId();
        assertTrue("Quellenliste in der Oberfläche", onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                SourceListPanel sources = shell.panel.transcript().sourcesFor(answerId);
                assertNotNull(sources);
                sources.setExpanded(true);
                shell.panel.doLayout();
                return sources.toggle().getText().startsWith("Quellen (");
            }
        }));

        FakeChatCompletionsServer.Recorded request = chatServer.lastRequest();
        assertEquals("Bearer " + TOKEN, request.authorization());
        assertEquals("application/json; charset=utf-8", request.contentType());
        assertTrue(request.streamRequested());
        assertTrue(request.systemContent(), request.systemContent().startsWith(SYSTEM_PROMPT + "\n\n"));
        assertTrue(request.systemContent().contains("--- KONTEXT ---"));
        assertTrue(request.systemContent().contains("[1] Kündigungsfrist"));
        assertTrue(request.systemContent().contains("drei Monate zum Quartalsende"));
        assertEquals("user: Wie lange ist die Kündigungsfrist?", request.messages().get(request.messages().size() - 1));
        assertEquals("die Frage wurde einmal eingebettet", chunks + 1, embeddingsServer.requestCount());
        assertEquals("Wie lange ist die Kündigungsfrist?",
                embeddingsServer.requests().get(chunks).inputs().get(0));
        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Kontext nicht in der Historie", 2, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());

        // 3. RAG aus: der bisherige Weg, ohne Embedding und ohne Kontext.
        chatServer.answerWith("Gern geschehen.");
        ask("Danke!", false);
        awaitIdle(shell.model);
        entries = entries(shell.model);
        assertEquals(4, entries.size());
        assertEquals("Gern geschehen.", entries.get(3).getText());
        assertFalse(entries.get(3).hasSources());
        assertEquals(SYSTEM_PROMPT, chatServer.lastRequest().systemContent());
        assertEquals("kein weiterer Embedding-Request", chunks + 1, embeddingsServer.requestCount());
        assertEquals("Historie läuft mit (System, Frage, Antwort, Frage)", 4,
                chatServer.lastRequest().messages().size());

        // 4. Stop: die Antwort hängt nach dem ersten Delta; Stop bricht ab, die Frage bleibt in der Historie.
        chatServer.answerAndHang("Ich überlege");
        ask("Wie viele Tage Urlaub habe ich?", true);
        awaitText(shell.model, "Ich überlege");
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(shell.panel.composer().stopButton().isEnabled());
                shell.panel.composer().stopButton().doClick();
            }
        });
        awaitIdle(shell.model);
        entries = entries(shell.model);
        TranscriptEntry cancelled = entries.get(5);
        assertEquals(TranscriptEntry.State.CANCELLED, cancelled.getState());
        assertEquals("Ich überlege", cancelled.getText());
        assertTrue("Quellen auch an der abgebrochenen Antwort", cancelled.hasSources());
        assertEquals("Urlaubsregelung", cancelled.getSources().get(0).getTitle());
        history = chat.conversation(conversation).messages();
        assertEquals(ChatRole.USER, history.get(history.size() - 1).role());
        assertEquals("Wie viele Tage Urlaub habe ich?", history.get(history.size() - 1).content());
        assertFalse(chat.isBusy(conversation));

        // 5. Fehler des Chat-Dienstes: Fehlerblase ohne technische Details.
        chatServer.failWith(500, "{\"error\":{\"message\":\"internal_error " + TOKEN + "\"}}");
        ask("Was gilt für Gleitzeit?", true);
        awaitIdle(shell.model);
        entries = entries(shell.model);
        TranscriptEntry failed = entries.get(entries.size() - 1);
        assertEquals(TranscriptEntry.State.FAILED, failed.getState());
        assertEquals("Fehler im KI-Dienst. (Status 500)", failed.getFailureMessage());
        assertTrue("Quellen wurden vor dem Fehler gefunden", failed.hasSources());

        // 6. Embedding-Dienst ausgefallen: Hinweis, Quellen nur aus der Volltextsuche.
        embeddingsServer.failWith(503, "{\"detail\":\"unavailable\"}");
        chatServer.answerWith("Neun bis fünfzehn Uhr [1].");
        ask("Wann ist Kernarbeitszeit?", true);
        awaitIdle(shell.model);
        entries = entries(shell.model);
        TranscriptEntry degraded = entries.get(entries.size() - 2);
        TranscriptEntry notice = entries.get(entries.size() - 1);
        assertEquals(TranscriptEntry.Author.ASSISTANT, degraded.getAuthor());
        assertEquals("Neun bis fünfzehn Uhr [1].", degraded.getText());
        assertTrue(degraded.hasSources());
        assertEquals("Gleitzeit", degraded.getSources().get(0).getTitle());
        assertEquals(0, degraded.getSources().get(0).getSemanticRank());
        assertEquals(TranscriptEntry.Author.NOTICE, notice.getAuthor());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE, notice.getText());
        embeddingsServer.recover();

        // 7. Nirgends ein Token: nicht im Verlauf, nicht in Quellen, nicht in Hinweisen, nicht in der Statuszeile.
        for (TranscriptEntry entry : entries(shell.model)) {
            assertFalse(entry.getText(), entry.getText().contains("SECRET"));
            assertFalse(entry.getFailureMessage(), entry.getFailureMessage().contains("SECRET"));
            for (SourceReference reference : entry.getSources()) {
                assertFalse(SourceListPanel.detailLine(reference).contains("SECRET"));
                assertFalse(SourceListPanel.titleLine(reference).contains("SECRET"));
            }
        }
        assertFalse(status.contains("SECRET"));
        onEdt(new Runnable() {
            @Override
            public void run() {
                shell.panel.setSize(820, 640);
                shell.panel.doLayout();
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(820, 640,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = image.createGraphics();
                try {
                    shell.panel.paint(g2);
                } finally {
                    g2.dispose();
                }
            }
        });
    }

    @Test
    public void cancellingTheIndexingFromTheStatusLineKeepsTheIndexedPages() throws Exception {
        final AtomicReference<IndexingReport> done = new AtomicReference<IndexingReport>();
        // Der Fake hält die erste Embedding-Antwort zurück; solange steht die erste Seite in Arbeit.
        embeddingsServer.hold();
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(shell.indexing.indexSource(source, SourceScope.of("urlaub", "kuendigung", "gleitzeit"),
                        new Consumer<IndexingReport>() {
                            @Override
                            public void accept(IndexingReport report) {
                                done.set(report);
                            }
                        }));
            }
        });
        assertTrue(embeddingsServer.awaitRequests(1, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        onEdt(new Runnable() {
            @Override
            public void run() {
                KnowledgeStatusBar statusBar = shell.panel.statusBar();
                assertTrue(statusBar.getText(), statusBar.getText().startsWith("Indexierung: 0 von 3 Seiten"));
                assertTrue(statusBar.cancelButton().isEnabled());
                statusBar.cancelButton().doClick(0);
                assertFalse("Abbruch nur einmal", statusBar.cancelButton().isEnabled());
                assertTrue(statusBar.getText(), statusBar.getText().endsWith(KnowledgeStatusBar.CANCELLING_SUFFIX));
            }
        });
        embeddingsServer.release();
        await("Indexierung beendet", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return done.get() != null;
            }
        });
        // Die laufende Seite wird noch fertig indexiert, danach endet der Lauf.
        assertTrue(done.get().toString(), done.get().isCancelled());
        assertEquals(done.get().toString(), 1, done.get().outcomes().size());
        assertEquals(1, done.get().count(IndexingStatus.INDEXED));
        String status = onEdt(new Callable<String>() {
            @Override
            public String call() {
                return shell.panel.statusBar().getText();
            }
        });
        assertTrue(status, status.startsWith("Indexierung abgebrochen: 1 von 3 Seiten indexiert (Stand "));
        assertFalse(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return shell.panel.statusBar().cancelButton().isVisible();
            }
        }));
    }

    private IndexingReport indexHandbuch() throws Exception {
        final AtomicReference<IndexingReport> done = new AtomicReference<IndexingReport>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(shell.indexing.indexSource(source, SourceScope.of("urlaub", "kuendigung", "gleitzeit"),
                        new Consumer<IndexingReport>() {
                            @Override
                            public void accept(IndexingReport report) {
                                done.set(report);
                            }
                        }));
                assertTrue(shell.panel.statusBar().isVisible());
                assertTrue(shell.panel.statusBar().cancelButton().isVisible());
            }
        });
        await("Indexierung beendet", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return done.get() != null;
            }
        });
        return done.get();
    }

    /** Tippt die Frage in die Eingabezeile, setzt den RAG-Schalter und sendet über die Oberfläche. */
    private void ask(final String question, final boolean rag) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                if (shell.panel.composer().ragToggle().isSelected() != rag) {
                    shell.panel.composer().ragToggle().doClick();
                }
                assertEquals(rag, shell.model.isRagEnabled());
                shell.panel.composer().editor().setText(question);
                assertTrue(shell.panel.composer().sendButton().isEnabled());
                shell.panel.composer().sendButton().doClick();
            }
        });
        assertTrue(chatServer.awaitRequest(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    }

    private final class Shell {
        final ChatShellModel model = new ChatShellModel(System::currentTimeMillis);
        final KnowledgeStatusModel status = new KnowledgeStatusModel();
        final RagChatBinding binding;
        final KnowledgeIndexingBinding indexing;
        final ChatShellPanel panel;

        Shell(RagChatUseCase rag, IndexKnowledgeUseCase indexUseCase) {
            binding = new RagChatBinding(rag, conversation, model, EDT, workers, ZoneOffset.UTC);
            indexing = new KnowledgeIndexingBinding(indexUseCase, status, EDT, workers, System::currentTimeMillis,
                    ZoneOffset.UTC);
            panel = new ChatShellPanel(model, binding, status, ComicPalette.defaultPalette(),
                    BubblePalette.windowsPhoneInspired());
            panel.setSize(820, 640);
        }
    }
}
