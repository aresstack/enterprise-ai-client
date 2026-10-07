package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeChatCompletionsServer;
import com.aresstack.enterpriseai.app.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatWindow;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.app.ui.chat.SourceListPanel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
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
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Lokaler Start der RAG-Shell (AP22): {@code ./gradlew :app-swing:runRagDemo}. Die echte Kette Shell →
 * {@link RagChatBinding} und {@link KnowledgeIndexingBinding} → Use Cases aus {@code application} (AP10) → echte
 * Adapter {@link OpenAiCompatibleChatAdapter} und {@link OpenAiCompatibleEmbeddingAdapter} über HTTP gegen die
 * lokalen Fake-Server des Integrationstests → {@link LuceneKnowledgeIndex} in einem temporären Verzeichnis,
 * {@code InMemoryKnowledgeSource} "handbuch" als Quelle.
 *
 * <p>Beim Start wird das Handbuch indexiert (Fortschritt und Ergebnis in der Statuszeile). Der Fake-Chat
 * streamt auf jede Frage eine Demo-Antwort, die die erste Quelle zitiert; "fehler" im Text simuliert HTTP 500,
 * "langsam" lässt die Antwort hängen, bis Stop gedrückt wird. Mit {@code --screenshot <datei.png>} läuft die
 * Demo ohne Fenster: indexieren, eine RAG-Frage stellen, Quellen aufklappen, Shell als PNG schreiben. Liegt
 * bewusst im Testumfang; die echte Composition Root folgt in AP23.
 */
public final class RagShellDemo {

    private static final String SYSTEM_PROMPT = "Antworte knapp und nenne die Quellen in eckigen Klammern.";
    private static final String DEMO_TOKEN = "demo-token";
    private static final int DIMENSION = 64;

    private RagShellDemo() {
    }

    public static void main(String[] args) throws Exception {
        String screenshot = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--screenshot".equals(args[i])) {
                screenshot = args[i + 1];
            }
        }
        final Backend backend = new Backend();
        if (screenshot != null) {
            try {
                screenshot(backend, new File(screenshot));
            } finally {
                backend.close();
            }
            System.exit(0);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                ComicTheme.installMenuDefaults(palette);
                Shell shell = new Shell(backend, palette, BubblePalette.windowsPhoneInspired());
                JFrame frame = ChatWindow.create("Enterprise AI Client – RAG-Demo", shell.panel, palette);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        backend.close();
                    }
                });
                frame.setVisible(true);
                shell.indexHandbuch(null);
            }
        });
    }

    /**
     * Ohne Fenster (headless) wird die Shell auf 820×640 gelegt und per rekursivem {@code doLayout} gesetzt;
     * mit Display (z. B. Xvfb) erscheint das echte {@link ChatWindow} und der Fensterinhalt wird gemalt.
     */
    private static void screenshot(final Backend backend, File target) throws Exception {
        final boolean headless = GraphicsEnvironment.isHeadless();
        final Shell shell = UiTestSupport.onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                ComicPalette palette = ComicPalette.defaultPalette();
                ComicTheme.installMenuDefaults(palette);
                return new Shell(backend, palette, BubblePalette.windowsPhoneInspired());
            }
        });
        final KnowledgeStatusModel status = shell.status;
        final JFrame[] frame = new JFrame[1];
        UiTestSupport.onEdt(new Runnable() {
            @Override
            public void run() {
                if (headless) {
                    shell.panel.setSize(820, 640);
                } else {
                    frame[0] = ChatWindow.create("Enterprise AI Client – RAG-Demo", shell.panel,
                            ComicPalette.defaultPalette());
                    frame[0].setSize(840, 700);
                    frame[0].setLocation(0, 0);
                    frame[0].setVisible(true);
                }
                shell.indexHandbuch(null);
            }
        });
        UiTestSupport.await("Indexierung", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !status.isRunning();
            }
        });
        UiTestSupport.onEdt(new Runnable() {
            @Override
            public void run() {
                shell.ask("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        UiTestSupport.awaitIdle(shell.model);
        UiTestSupport.onEdt(new Runnable() {
            @Override
            public void run() {
                shell.ask("Danke!", false);
            }
        });
        UiTestSupport.awaitIdle(shell.model);
        UiTestSupport.onEdt(new Runnable() {
            @Override
            public void run() {
                List<TranscriptEntry> entries = shell.model.getEntries();
                for (TranscriptEntry entry : entries) {
                    if (entry.hasSources()) {
                        SourceListPanel sources = shell.panel.transcript().sourcesFor(entry.getId());
                        sources.setExpanded(true);
                    }
                }
                if (headless) {
                    invalidateTree(shell.panel);
                    layoutTree(shell.panel);
                } else {
                    frame[0].validate();
                }
            }
        });
        if (!headless) {
            Thread.sleep(500L);
        }
        final BufferedImage[] image = new BufferedImage[1];
        UiTestSupport.onEdt(new Runnable() {
            @Override
            public void run() {
                Component painted = headless ? shell.panel : frame[0].getContentPane();
                image[0] = new BufferedImage(painted.getWidth(), painted.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = image[0].createGraphics();
                try {
                    painted.paint(g2);
                } finally {
                    g2.dispose();
                }
                if (frame[0] != null) {
                    frame[0].dispose();
                }
            }
        });
        target.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image[0], "png", target);
        System.out.println("Screenshot: " + target.getAbsolutePath());
    }

    /** Ohne Peer wandert {@code invalidate()} nicht nach oben; die Layout-Caches werden daher einzeln geleert. */
    private static void invalidateTree(Component component) {
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                invalidateTree(child);
            }
        }
        component.invalidate();
    }

    /** Ersatz für {@code validate()} ohne Peer: Layout von oben nach unten durch den ganzen Baum. */
    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }

    /** Fake-Server, echte Adapter, Lucene-Index im temporären Verzeichnis und die Use Cases aus AP10. */
    private static final class Backend {
        final FakeChatCompletionsServer chatServer;
        final FakeEmbeddingsServer embeddingsServer;
        final Path indexDirectory;
        final LuceneKnowledgeIndex index;
        final ChatService chat;
        final ChatConversationId conversation;
        final RagChatUseCase rag;
        final IndexKnowledgeUseCase indexing;
        final ExecutorService workers = UiTestSupport.workers("rag-demo");
        final InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("handbuch")
                .add("urlaub", "Urlaubsregelung", "Mitarbeiterinnen und Mitarbeiter haben dreißig Tage Urlaub im "
                        + "Kalenderjahr. Resturlaub verfällt am 31. März des Folgejahres, wenn er nicht beantragt "
                        + "wurde.")
                .add("kuendigung", "Kündigungsfrist", "Die Kündigungsfrist beträgt drei Monate zum Quartalsende. "
                        + "Die Frist gilt für beide Seiten; eine Kündigung muss schriftlich erfolgen.")
                .add("gleitzeit", "Gleitzeit", "Die Kernarbeitszeit liegt zwischen neun und fünfzehn Uhr. Außerhalb "
                        + "der Kernarbeitszeit kann die Arbeitszeit frei gestaltet werden.")
                .add("homeoffice", "Mobiles Arbeiten", "Mobiles Arbeiten ist an bis zu drei Tagen pro Woche "
                        + "möglich. Die Abstimmung erfolgt im Team; ein Antrag ist nicht nötig.");

        Backend() throws IOException {
            chatServer = new FakeChatCompletionsServer();
            embeddingsServer = new FakeEmbeddingsServer(DIMENSION);
            OpenAiCompatibleChatAdapter chatAdapter = new OpenAiCompatibleChatAdapter(
                    OpenAiCompatibleChatConfig.builder(chatServer.baseUrl(), "openai/gpt-oss-120b")
                            .bearerToken(OpenAiCompatibleChatConfig.TokenSource.fixed(DEMO_TOKEN))
                            .build());
            OpenAiCompatibleEmbeddingAdapter embeddingAdapter = new OpenAiCompatibleEmbeddingAdapter(
                    OpenAiCompatibleEmbeddingConfiguration.builder(embeddingsServer.baseUrl(),
                            "danielheinz/e5-base-sts-en-de", DIMENSION).build(),
                    new BearerTokenSource() {
                        @Override
                        public char[] bearerToken() {
                            return DEMO_TOKEN.toCharArray();
                        }
                    });
            EmbeddingModelIdentity space = embeddingAdapter.modelIdentity();
            indexDirectory = Files.createTempDirectory("rag-demo-index");
            index = new LuceneKnowledgeIndex(indexDirectory);
            chat = new ChatService(chatAdapter);
            conversation = chat.openConversation(SYSTEM_PROMPT);
            rag = new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddingAdapter, space, null),
                    new PromptContextAssembler(null));
            indexing = new IndexKnowledgeUseCase(index, embeddingAdapter, space,
                    new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        }

        /** Legt dem Fake-Chat vor jeder Frage die passende gestreamte Antwort ins Skript. */
        void script(String question, boolean ragEnabled) {
            String lower = question.toLowerCase(Locale.ROOT);
            if (lower.contains("fehler")) {
                chatServer.failWith(500, "{\"error\":{\"message\":\"internal_error\"}}");
                return;
            }
            String text = ragEnabled
                    ? "Laut Handbuch [1] lautet die Antwort auf \"" + question + "\": siehe den zitierten "
                    + "Abschnitt. Diese Demo-Antwort kommt gestreamt vom lokalen Fake-Server; der Kontext aus der "
                    + "Wissensbasis stand als Systemblock im Request."
                    : "Antwort ohne Wissensbasis auf \"" + question + "\": der Request enthielt nur den Verlauf, "
                    + "kein Embedding wurde berechnet.";
            String[] words = text.split(" ");
            String[] deltas = new String[words.length];
            for (int i = 0; i < words.length; i++) {
                deltas[i] = (i == 0 ? "" : " ") + words[i];
            }
            if (lower.contains("langsam")) {
                chatServer.answerAndHang(deltas);
            } else {
                chatServer.answerWith(deltas);
            }
        }

        void close() {
            workers.shutdownNow();
            chatServer.close();
            embeddingsServer.close();
            index.close();
            deleteRecursively(indexDirectory.toFile());
        }

        private static void deleteRecursively(File file) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
            file.delete();
        }
    }

    /** Shell, Bindings und Statusmodell wie sie AP23 in der Composition Root verdrahtet. */
    private static final class Shell {
        final ChatShellModel model = new ChatShellModel(System::currentTimeMillis);
        final KnowledgeStatusModel status = new KnowledgeStatusModel();
        final RagChatBinding binding;
        final KnowledgeIndexingBinding indexing;
        final ChatShellPanel panel;
        private final Backend backend;

        Shell(final Backend backend, ComicPalette palette, BubblePalette bubbles) {
            this.backend = backend;
            binding = new RagChatBinding(backend.rag, backend.conversation, model, SwingUtilities::invokeLater,
                    backend.workers, ZoneId.systemDefault());
            indexing = new KnowledgeIndexingBinding(backend.indexing, status, SwingUtilities::invokeLater,
                    backend.workers, System::currentTimeMillis, ZoneId.systemDefault());
            panel = new ChatShellPanel(model, new ChatShellActions() {
                @Override
                public void sendRequested(String text, boolean ragEnabled) {
                    backend.script(text, ragEnabled);
                    binding.sendRequested(text, ragEnabled);
                }

                @Override
                public void stopRequested() {
                    binding.stopRequested();
                }
            }, status, palette, bubbles);
            model.setRagEnabled(true);
        }

        void indexHandbuch(Consumer<IndexingReport> onDone) {
            indexing.indexSource(backend.source, SourceScope.of("urlaub", "kuendigung", "gleitzeit", "homeoffice"),
                    onDone == null ? new Consumer<IndexingReport>() {
                        @Override
                        public void accept(IndexingReport report) {
                        }
                    } : onDone);
        }

        void ask(String question, boolean rag) {
            if (panel.composer().ragToggle().isSelected() != rag) {
                panel.composer().ragToggle().doClick(0);
            }
            panel.composer().editor().setText(question);
            panel.composer().sendButton().doClick(0);
        }
    }
}
