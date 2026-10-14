package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchWorkflow;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.AgentBackend;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ResearchWorkflowAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aresstack.enterpriseai.proasteion.exedra.assembly.AgentModeEndToEndTest.assertOnlyQuestionAndAnswer;
import static com.aresstack.enterpriseai.proasteion.exedra.assembly.AgentModeEndToEndTest.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Der UI-Lauf über die ganze Anwendung: Composition Root mit dem eingebauten Recherche-Agenten als echtem Kindprozess
 * (ACP, MCP), {@link ShellAssembly} mit Composer, Phasen-Dropdown, Drawer-Reitern und Play-Knöpfen, eine durchsuchbare
 * Wissensquelle, deren erster Treffer länger hängt als die MCP-Frist des Agenten. Jeder Schritt wird so bedient wie von
 * der Nutzerin (Composer, Dropdown-Wahl, Reiter, Play-Knopf) und als Bild der echten Oberfläche nach
 * {@code build/ui-run} geschrieben.
 *
 * <p>Szenarien: A (Konzeptfrage: nur Frage und Antwort bleiben), B (Zeitüberschreitung bei Treffer 1, Antwort aus
 * Treffer 2, keine Verbindungsmeldung), F (Reiterwechsel ändert die Phase nicht, Play Quellen in Phase Dokument
 * gesperrt), C (Play Quellen, Phase bleibt), D (Play Gliederung), E (Play Dokument).
 */
public class AgentPhaseUiRunTest {

    private static final String QUESTION = "Suche in Confluence nach IAR";
    private static final String FOLLOW_UP = "Wer ist bei IAR für die Meilensteine zuständig?";
    private static final String SLOW_KEY = "1001";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private String previousConfig;
    private CompositionRoot root;
    private ShellAssembly.ShellView view;
    private SlowFirstHit source;
    private final File shots = new File("build/ui-run");
    private final AtomicInteger shot = new AtomicInteger();
    private final List<TranscriptEntry> clouds = new CopyOnWriteArrayList<TranscriptEntry>();

    /** Die Wissensquelle: Treffer 1 hängt beim Lesen länger als die MCP-Frist des Agenten. */
    static final class SlowFirstHit implements SearchableKnowledgeSource {
        private final InMemoryKnowledgeSource pages = new InMemoryKnowledgeSource("wiki")
                .add(SLOW_KEY, "IAR Übersicht", "IAR bündelt die Auswertung der Anträge.\n\n"
                        + "Zuständig ist das Team Auswertung.")
                .add("1002", "IAR Planung", "Der Pilot von IAR startet im Frühjahr.\n\n"
                        + "Die Planung von IAR verantwortet das Referat Planung.")
                .add("1003", "IAR Meilensteine", "Meilenstein 1 von IAR ist die Fachkonzeption bis Juni.")
                .add("1004", "IAR Betrieb", "Den Betrieb von IAR übernimmt das Rechenzentrum.");
        final List<String> slowLoads = new CopyOnWriteArrayList<String>();
        volatile long slowMillis = 9_000L;

        @Override
        public SourceAddress sourceId() {
            return pages.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            return pages.discover(scope);
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            if (resourceId.equals(pages.idOf(SLOW_KEY))) {
                slowLoads.add(Thread.currentThread().getName());
                try {
                    Thread.sleep(slowMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return pages.load(resourceId);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return pages.discoverLinks(resourceId);
        }

        @Override
        public List<SourceSearchHit> search(SourceQuery query) throws KnowledgeSourceException {
            return pages.search(query);
        }
    }

    @Before
    public void composeApplication() throws Exception {
        String classPath = System.getProperty("research.agent.classpath");
        assumeTrue("research agent class path not set (run via Gradle)", classPath != null);
        previousConfig = System.getProperty(AppPaths.CONFIG_PROPERTY);
        System.setProperty(AppPaths.CONFIG_PROPERTY,
                temp.getRoot().toPath().resolve("enterprise-ai-client.properties").toString());
        assertTrue(shots.isDirectory() || shots.mkdirs());

        final SolonMcpServerRuntime mcp = new SolonMcpServerRuntime();
        AgentBackend research = AdapterAssembly.researchAgent(mcp);
        List<String> args = new ArrayList<String>(research.launchSpec().getArgs());
        args.set(args.indexOf("-cp") + 1, classPath);
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put(EnvironmentRunBudget.ENV_MAX_PAGES, "4");
        // kurze MCP-Frist des Agenten: die hängende Seite läuft wirklich in die Zeitüberschreitung
        env.put("ENTERPRISE_AI_RESEARCH_MCP_TIMEOUT_SECONDS", "4");
        AgentBackend backend = new AgentBackend(research.connector(),
                new AgentLaunchSpec(research.launchSpec().getCommand(), args, env), research.registry(),
                research.endpoint());
        source = new SlowFirstHit();
        final ResearchAgentHost[] host = new ResearchAgentHost[1];
        AgentModeEndToEndTest.ScriptedModel script = new AgentModeEndToEndTest.ScriptedModel(() -> host[0]);
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(64);
        ApplicationPorts ports = ApplicationPorts.builder()
                .chat(script)
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(new InMemoryKnowledgeIndex())
                .sources(KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(source, SourceScope.of(SLOW_KEY))))
                .secrets(new UnavailableSecretProvider("Test ohne KeePass"))
                .researchAgent(backend)
                .closing("mcp-server", new Closeable() {
                    @Override
                    public void close() {
                        mcp.shutdown();
                    }
                })
                .build();
        root = CompositionRoot.compose(TestConfigs.withoutAgent(temp.newFolder("index").toPath()), ports,
                SwingUtilities::invokeLater, System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        host[0] = root.researchHost();
        view = onEdt(() -> ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired()));
        onEdt(() -> {
            view.chatModel().addListener(new ChatShellModelListener() {
                @Override
                public void entryAdded(TranscriptEntry entry) {
                    if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                        clouds.add(entry);
                    }
                }

                @Override
                public void entryUpdated(TranscriptEntry entry) {
                }

                @Override
                public void stateChanged() {
                }
            });
            view.workspace().modes().select(ShellMode.AGENT);
            return null;
        });
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.shutdown().run();
        }
        if (previousConfig == null) {
            System.clearProperty(AppPaths.CONFIG_PROPERTY);
        } else {
            System.setProperty(AppPaths.CONFIG_PROPERTY, previousConfig);
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    @Test
    public void theAgentUiFollowsTheAskAiModelThroughTheWholeApplication() throws Exception {
        ResearchAgentHost host = root.researchHost();
        ChatComposerPanel composer = onEdt(() -> view.workspace().visibleShell().composer());
        snapshot("start");

        // Komponist: Modell, Denken und als drittes Dropdown die Phase; kein roter Startknopf, keine Phasenleiste
        assertTrue(onEdt(() -> composer.phaseButton().isShowing() || composer.phaseButton().isVisible()));
        assertEquals("Phase: Konzept", onEdt(() -> composer.phaseButton().getText()));
        assertFalse("kein „Recherche starten“ über dem Composer", onEdt(() -> hasButtonText(view.shell(),
                "Recherche starten")));

        // A + B: Konzeptfrage mit Recherche; Treffer 1 hängt, Treffer 2 liefert die Antwort
        send(composer, QUESTION, "recherche-laeuft");
        List<TranscriptEntry> turn = entries();
        snapshot("nach-der-antwort");
        assertOnlyQuestionAndAnswer(turn, QUESTION);
        String answer = turn.get(1).getText();
        assertTrue(answer, answer.contains("Der Pilot von IAR startet im Frühjahr"));
        assertTrue("Treffer 1 wurde gelesen und hing: " + source.slowLoads, source.slowLoads.size() >= 2);
        assertTrue("es gab Wolken während des Turns", !clouds.isEmpty());
        assertEquals("scoping", host.phaseId());

        send(composer, FOLLOW_UP, null);
        assertOnlyQuestionAndAnswer(entries().subList(2, entries().size()), FOLLOW_UP);
        assertEquals("scoping", host.phaseId());

        // F: Reiter wechseln ändert die Phase nicht
        onEdt(() -> {
            view.workspace().openDrawer();
            view.workspace().selectWorkspaceTab(ResearchWorkflowAssembly.SOURCES_TAB);
            return null;
        });
        snapshot("reiter-quellen-im-konzept");
        assertFalse(onEdt(() -> play(ResearchWorkflow.PHASE_SOURCES).isEnabled()));
        assertEquals("Phase ‚Quellen‘ auswählen, um die Recherche zu starten.",
                onEdt(() -> play(ResearchWorkflow.PHASE_SOURCES).getToolTipText()));
        choosePhase(composer, ResearchWorkflow.PHASE_DOCUMENT);
        for (String tab : new String[]{ResearchWorkflowAssembly.DOCUMENT_TAB, ResearchWorkflowAssembly.OUTLINE_TAB,
                ResearchWorkflowAssembly.SOURCES_TAB}) {
            onEdt(() -> {
                view.workspace().selectWorkspaceTab(tab);
                return null;
            });
            assertEquals("Reiter " + tab + " ändert die Phase nicht", "draft", host.phaseId());
            assertEquals("Phase: Dokument", onEdt(() -> composer.phaseButton().getText()));
        }
        snapshot("phase-dokument-reiter-quellen");
        assertFalse("Play Quellen in Phase Dokument gesperrt",
                onEdt(() -> play(ResearchWorkflow.PHASE_SOURCES).isEnabled()));
        assertTrue(onEdt(() -> play(ResearchWorkflow.PHASE_DOCUMENT).isEnabled()));
        assertEquals("Dokument schreiben", onEdt(() -> play(ResearchWorkflow.PHASE_DOCUMENT).getToolTipText()));

        // C: Phase Quellen, Play „Recherche starten“: der Lauf läuft, die Phase bleibt
        choosePhase(composer, ResearchWorkflow.PHASE_SOURCES);
        int before = entries().size();
        clickPlay(ResearchWorkflow.PHASE_SOURCES, "quellen-lauf");
        snapshot("quellen-nach-dem-lauf");
        assertEquals("research", host.phaseId());
        assertTrue(host.runStatus().hasRun());
        assertFalse(host.sourceRefs().isEmpty());
        assertOnlyQuestionAndAnswer(entries().subList(before, entries().size()), "Recherche starten");

        // D: Phase Gliederung, Play „Gliederung erstellen“
        choosePhase(composer, ResearchWorkflow.PHASE_OUTLINE);
        onEdt(() -> {
            view.workspace().selectWorkspaceTab(ResearchWorkflowAssembly.OUTLINE_TAB);
            return null;
        });
        before = entries().size();
        clickPlay(ResearchWorkflow.PHASE_OUTLINE, null);
        snapshot("gliederung");
        assertEquals("outline", host.phaseId());
        assertFalse(host.outline().trim().isEmpty());
        assertOnlyQuestionAndAnswer(entries().subList(before, entries().size()), "Gliederung erstellen");

        // E: Phase Dokument, Play „Dokument schreiben“
        choosePhase(composer, ResearchWorkflow.PHASE_DOCUMENT);
        onEdt(() -> {
            view.workspace().selectWorkspaceTab(ResearchWorkflowAssembly.DOCUMENT_TAB);
            return null;
        });
        before = entries().size();
        clickPlay(ResearchWorkflow.PHASE_DOCUMENT, "dokument-schreibt");
        snapshot("dokument");
        assertEquals("draft", host.phaseId());
        assertTrue(host.document() != null);
        assertOnlyQuestionAndAnswer(entries().subList(before, entries().size()), "Dokument schreiben");
    }

    // ------------------------------------------------------------------ Bedienung wie die Nutzerin

    private void send(final ChatComposerPanel composer, final String text, String during) throws Exception {
        onEdt(() -> {
            composer.editor().setText(text);
            assertTrue("Senden muss möglich sein", composer.sendButton().isEnabled());
            composer.sendButton().doClick();
            return null;
        });
        awaitIdle(during);
    }

    /** Die Wahl im Phasen-Dropdown (derselbe Weg wie der Menüeintrag). */
    private void choosePhase(final ChatComposerPanel composer, final String phaseId) throws Exception {
        onEdt(() -> {
            composer.selectPhase(phaseId);
            return null;
        });
        onEdt(() -> null);
        assertEquals(phaseId, root.researchHost().phaseId());
        assertEquals("Phase: " + ResearchWorkflow.titleOf(phaseId), onEdt(() -> composer.phaseButton().getText()));
    }

    private void clickPlay(final String phaseId, String during) throws Exception {
        onEdt(() -> {
            AbstractButton button = play(phaseId);
            assertTrue("Play " + phaseId + " aktiv", button.isEnabled());
            button.doClick();
            return null;
        });
        awaitIdle(during);
        assertEquals("Play wechselt die Phase nicht", phaseId, root.researchHost().phaseId());
    }

    private AbstractButton play(String phaseId) {
        Component found = byName(view.shell(), "research-play-" + phaseId);
        if (!(found instanceof AbstractButton)) {
            throw new AssertionError("kein Play-Knopf " + phaseId);
        }
        return (AbstractButton) found;
    }

    private static Component byName(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = byName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasButtonText(Component component, String text) {
        if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText())
                && component.isVisible()) {
            return true;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (hasButtonText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<TranscriptEntry> entries() throws Exception {
        final ChatShellModel model = view.chatModel();
        return onEdt(() -> new ArrayList<TranscriptEntry>(model.getEntries()));
    }

    /** Wartet auf das Turn-Ende; {@code during} hält einmal das Bild fest, während Wolken sichtbar sind. */
    private void awaitIdle(String during) throws Exception {
        final ChatShellModel model = view.chatModel();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(240);
        boolean streaming;
        boolean captured = during == null;
        do {
            Thread.sleep(100L);
            streaming = onEdt(() -> model.isStreaming());
            if (!captured && streaming && hasActivity(model)) {
                Thread.sleep(1_200L); // die Wolke ist eingeblendet
                snapshot(during);
                captured = true;
            }
        } while (streaming && System.nanoTime() < deadline);
        assertFalse("agent turn did not end", streaming);
        onEdt(() -> null);
    }

    private static boolean hasActivity(final ChatShellModel model) throws Exception {
        return onEdt(() -> {
            for (TranscriptEntry entry : model.getEntries()) {
                if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Zeichnet die echte Oberfläche (dieselben Komponenten wie im Fenster) in ein Bild unter build/ui-run. */
    private void snapshot(final String name) throws Exception {
        final File file = new File(shots, String.format("%02d-%s.png", shot.incrementAndGet(), name));
        onEdt(() -> {
            JComponent shell = (JComponent) view.shell();
            shell.setSize(1280, 860);
            layout(shell);
            // wie die Anwendung: der Verlauf steht am Ende (das Nachscrollen läuft sonst erst im nächsten Takt)
            scrollToEnd(view.workspace().visibleShell());
            layout(shell);
            BufferedImage image = new BufferedImage(1280, 860, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                shell.printAll(g);
            } finally {
                g.dispose();
            }
            ImageIO.write(image, "png", file);
            return null;
        });
    }

    private static void scrollToEnd(Component component) {
        if (component instanceof javax.swing.JScrollPane) {
            javax.swing.JScrollBar bar = ((javax.swing.JScrollPane) component).getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                scrollToEnd(child);
            }
        }
    }

    private static void layout(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

}
