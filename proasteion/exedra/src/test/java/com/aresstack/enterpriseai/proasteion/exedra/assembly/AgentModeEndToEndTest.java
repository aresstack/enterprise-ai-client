package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.proasteion.application.agent.ChatPortMainModelChat;
import com.aresstack.enterpriseai.proasteion.application.agent.PhaseToolSetController;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDocument;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchScopeControl;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchSourceRef;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchWorkflow;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.AgentBackend;
import com.aresstack.enterpriseai.proasteion.exedra.agent.AgentServiceBinding;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ResearchWorkflowAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Ende-zu-Ende-Abnahme des Agent-Modus über die echte Kette, ohne einen einzigen direkten Host-Werkzeugaufruf für
 * Schritte, die der Agent machen soll: {@link ChatShellModel} (Verlauf, wie die Oberfläche ihn zeichnet) →
 * {@link AgentServiceBinding} → {@link AgentService} → ACP (Solon-Connector) → {@code ResearchAgentMain} als
 * Kindprozess → MCP-Endpoint des Hosts ({@link ResearchControlTools}, {@link PhaseToolSetController}) →
 * {@code knowledge_search}/{@code knowledge_read} einer Fake-Quelle → ACP-Updates → Denk-, Werkzeug- und
 * Recherche-Wolken → sichtbare Agentenantwort. Phasenwechsel kommen ausschließlich aus den Aktions-Tags der
 * Phasenleiste ({@link ResearchWorkflowAssembly}, wie {@code ResearchWorkflowAcceptanceTest}); das Hauptmodell ist ein
 * Skript, das seine Antworten aus den Auszügen baut, die es vom Agenten bekommt.
 *
 * <p>Abnahmeszenarien 2 (Direktrecherche in Phase 1), 3 (Folgefrage ohne Phasenzwang), 4 („Recherche starten“ erst
 * mit Konzept, autonomer Lauf aus Konzept und Weidezaun), 5 (Quellen freigeben → Gliederung), 6 (Gliederung freigeben
 * → Kapitel nacheinander → persistiertes Dokument) und 9 (Unicode über den ganzen Agentenprozess).
 */
public class AgentModeEndToEndTest {

    /** Regressionstext des Nutzers, wörtlich. */
    static final String UNICODE = "äöü ÄÖÜ ß – … €";

    private static final String SEARCH_REQUEST = "Suche in Confluence nach IAR";
    private static final String FOLLOW_UP = "Wer ist bei IAR für die Meilensteine zuständig?";
    private static final String EXCLUDED = "Archiv";

    /** Die Fake-Quelle: fünf Confluence-Seiten; die Suchtreffer verraten die Fakten nicht, nur das Lesen. */
    private static final String[][] PAGES = {
            {"confluence:wiki/page/1001", "IAR Übersicht",
                    "IAR bündelt die Auswertung der Anträge.\n\nZuständig ist das Team Auswertung.\n\n"
                            + "Sonderzeichen im Projekt: " + UNICODE},
            {"confluence:wiki/page/1002", "IAR Planung",
                    "Der Pilot von IAR startet im Frühjahr.\n\nDie Planung verantwortet das Referat Planung."},
            {"confluence:wiki/page/1003", "IAR Meilensteine",
                    "Meilenstein 1 von IAR ist die Fachkonzeption bis Juni."},
            {"confluence:wiki/page/1004", "IAR Betrieb",
                    "Den Betrieb von IAR übernimmt das Rechenzentrum."},
            // Semantisch klar auf der OUT-Seite: der Weidezaun (askai SearchScopeGate, Nähe 0,7) verwirft nie wegen
            // eines einzelnen Wortes, nur nach dem Inhalt; mit dem Wortbeutel-Fake wäre "IAR Archiv" unentschieden.
            // Der Test prüft die Verdrahtung des Zauns; seine Trennschärfe gehört dem echten Embedding.
            {"confluence:wiki/page/1005", EXCLUDED,
                    "Das " + EXCLUDED + " enthält Altakten aus der Ablage."},
    };

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /**
     * Erwartungen, an denen spätere Schritte nicht hängen, werden gesammelt statt abzubrechen: so zeigt ein Lauf alle
     * fehlenden Verhalten einer Kette auf einmal (Abnahmeliste).
     */
    @Rule
    public final ErrorCollector expected = new ErrorCollector();

    private SolonMcpServerRuntime mcp;
    private ExecutorService starts;
    private AgentService service;
    private File projects;
    private ResearchAgentHost host;
    private ScriptedModel script;
    private ChatShellModel chat;
    private AgentServiceBinding binding;
    private ResearchWorkflowAssembly.Parts workflow;
    private final List<String> knowledgeCalls = new CopyOnWriteArrayList<String>();
    private final List<String> readIds = new CopyOnWriteArrayList<String>();
    private final List<String> sentActions = new CopyOnWriteArrayList<String>();
    private final CardRecorder cards = new CardRecorder();

    @Before
    public void startAgentProcess() throws Exception {
        String classPath = System.getProperty("research.agent.classpath");
        assumeTrue("research agent class path not set (run via Gradle)", classPath != null);
        mcp = new SolonMcpServerRuntime();
        AgentBackend backend = AdapterAssembly.researchAgent(mcp);
        List<String> args = new ArrayList<String>(backend.launchSpec().getArgs());
        args.set(args.indexOf("-cp") + 1, classPath);
        Map<String, String> env = new LinkedHashMap<String, String>();
        // Seitenbudget des autonomen Laufs wie die Einstellung agent.research.maxPages (kippbar: 4)
        env.put(EnvironmentRunBudget.ENV_MAX_PAGES, "4");

        projects = folder.newFolder("research");
        host = newHost(projects);
        host.setCorpusSearch(this::corpusSearch);
        // wie die Composition Root: der Weidezaun misst über den Embedding-Port (hier Wortbeutel-Fake)
        final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(256);
        host.setScopeEmbedder(new ResearchScopeControl.Embedder() {
            @Override
            public String fingerprint() {
                return embeddings.modelIdentity().fingerprint();
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                List<float[]> vectors = new ArrayList<float[]>();
                for (EmbeddingVector vector : embeddings.embed(texts).vectors()) {
                    vectors.add(vector.values());
                }
                return vectors;
            }
        });
        script = new ScriptedModel(host);
        final ChatPortMainModelChat model = new ChatPortMainModelChat(script, ChatOptions.defaults());
        // wie die Composition Root: der Werkzeugsatz folgt dem Zustand des Hosts
        final PhaseToolSetController toolSet = new PhaseToolSetController(() -> {
            List<McpToolContribution> tools = new ArrayList<McpToolContribution>(
                    ResearchControlTools.toolsFor(host, model));
            tools.addAll(fakeSource());
            return tools;
        });
        host.addStateListener(toolSet::refresh);
        AcpAgentLauncher launcher = AcpAgentLauncher.withPhaseToolSet(backend.connector(),
                new AgentLaunchSpec(backend.launchSpec().getCommand(), args, env), backend.registry(),
                backend.endpoint(), toolSet);
        starts = Executors.newSingleThreadExecutor();
        service = new AgentService(launcher, starts);

        onEdt(() -> {
            chat = new ChatShellModel(System::currentTimeMillis);
            chat.addListener(cards);
            binding = new AgentServiceBinding(service, chat, SwingUtilities::invokeLater);
            // Phasenleiste und Reiter wie in ShellAssembly: die Aktion schickt ihren Auftrag über die Bindung
            workflow = ResearchWorkflowAssembly.create(host, (visible, prompt) -> {
                sentActions.add(visible);
                binding.sendAction(visible, prompt);
            }, chat, SwingUtilities::invokeLater, (resourceId, title) -> {
            }, title -> {
            });
            return null;
        });
    }

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (starts != null) {
            starts.shutdownNow();
        }
        if (mcp != null) {
            mcp.shutdown();
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    // ------------------------------------------------------------------ Szenario 2

    @Test
    public void scenario2_phase1SearchShowsResearchCardBudgetToolCloudsAndAnswersFromReadPages() throws Exception {
        List<TranscriptEntry> entries = ask(SEARCH_REQUEST);

        // Phase 1 bleibt Phase 1: kein autonomer Lauf, kein Phasenwechsel
        assertEquals("scoping", host.phaseId());
        assertFalse("kein autonomer Lauf in Phase 1", host.runStatus().hasRun());

        // knowledge_search, dann knowledge_read der besten Seiten, im Seitenbudget 4
        assertTrue(knowledgeCalls.toString(), knowledgeCalls.contains("knowledge_search IAR"));
        assertEquals(knowledgeCalls.toString(), 4, readIds.size());
        assertTrue(knowledgeCalls.toString(), knowledgeCalls.indexOf("knowledge_search IAR")
                < knowledgeCalls.indexOf("knowledge_read " + PAGES[0][0]));

        // die Recherche-Wolke: Titel, Art, Budget 0/4 … 4/4 mit Balken in den Zwischenständen, Bilanz am Ende
        TranscriptEntry card = activity(entries, "Recherche in Confluence");
        expected.checkThat("Art der Recherche-Wolke nach ACP (ChatTranscriptPanel zeichnet nur \"research\" als Karte)",
                card.getActivityKind(), is("research"));
        assertEquals(TranscriptEntry.State.COMPLETE, card.getState());
        assertEquals("abgeschlossen: 4 Seiten gelesen, 5 Treffer, 4 Quellen übernommen",
                card.getActivitySummary());
        List<String> states = cards.texts(card.getActivityKey());
        for (int pages = 0; pages <= 4; pages++) {
            assertTrue("Zwischenstand Seiten " + pages + " / 4 in " + states,
                    containsBoth(states, "Seiten: " + pages + " / 4", "[[bar:" + pages + "/4]]"));
        }
        assertTrue(states.toString(), containsBoth(states, "Wissensquelle: Confluence", "Treffer: 5"));

        // sichtbare Werkzeugaufrufe: Suche mit Anfrage und Treffern, jede gelesene Seite, die Notiz als Quelle
        TranscriptEntry search = activity(entries, "Suche in den Wissensquellen");
        assertEquals("IAR", search.getText());
        assertEquals(TranscriptEntry.State.COMPLETE, search.getState());
        assertTrue(search.getActivitySummary(), search.getActivitySummary().startsWith("5 Treffer"));
        List<String> readTitles = new ArrayList<String>();
        for (TranscriptEntry entry : activities(entries, "Lese Seite")) {
            assertEquals(entry.getText() + ": " + entry.getActivitySummary(), TranscriptEntry.State.COMPLETE,
                    entry.getState());
            readTitles.add(entry.getText());
        }
        assertEquals(Arrays.asList(PAGES[0][1], PAGES[1][1], PAGES[2][1], PAGES[3][1]), readTitles);
        assertEquals(4, activities(entries, "Quelle notieren").size());
        assertTrue("Denkschritte als grüne Wolke", activity(entries, "Denke nach").isThinking());

        // die gelesenen Seiten sind Quellen des Projekts
        assertEquals(Arrays.asList(PAGES[0][0], PAGES[1][0], PAGES[2][0], PAGES[3][0]), sourceIds());

        // die Antwort entsteht aus den gelesenen Seiten (Fakten, die nur im Seitentext stehen) mit Belegen
        String answer = lastAnswer(entries);
        assertTrue(answer, answer.contains("Zuständig ist das Team Auswertung"));
        assertTrue(answer, answer.contains("Der Pilot von IAR startet im Frühjahr"));
        assertTrue(answer, answer.matches("(?s).*\\[\\d+\\].*"));
        assertTrue(answer, answer.contains("Quellen:") && answer.contains(PAGES[0][1]));
        assertFalse(answer, answer.toLowerCase(Locale.ROOT).contains("keinen zugriff"));
        assertNoMojibake(entries);
        assertNoNotice(entries);
    }

    // ------------------------------------------------------------------ Szenario 3

    @Test
    public void scenario3_followUpQuestionInPhase1IsAnsweredWithoutForcingAPhaseChange() throws Exception {
        ask(SEARCH_REQUEST);
        int before = snapshot().size();
        List<TranscriptEntry> entries = ask(FOLLOW_UP);
        List<TranscriptEntry> turn = entries.subList(before, entries.size());

        // keine Phase gewechselt, kein Lauf gestartet, keine Aktion ausgelöst
        assertEquals("scoping", host.phaseId());
        assertFalse(host.runStatus().hasRun());
        assertTrue(sentActions.toString(), sentActions.isEmpty());
        // der Startknopf bleibt ein Angebot, kein Zwang
        assertEquals(Arrays.asList("Recherche starten"), onEdt(() -> workflow.strip().actionLabels()));

        // die Frage wird inhaltlich beantwortet
        String answer = lastAnswer(turn);
        assertTrue(answer, answer.contains("Meilenstein 1 von IAR ist die Fachkonzeption bis Juni")
                || answer.contains("Zuständig ist das Team Auswertung"));
        assertFalse(answer, answer.toLowerCase(Locale.ROOT).contains("erst die recherche starten"));
        assertNoNotice(turn);
        assertNoMojibake(turn);
    }

    // ------------------------------------------------------------------ Szenario 4

    @Test
    public void scenario4_startResearchWithoutConceptIsRefusedAndStaysInPhase1() throws Exception {
        refresh();
        perform(ResearchWorkflow.START_RESEARCH);
        awaitIdle();

        assertEquals("ohne Konzept beginnt keine Phase 2", "scoping", host.phaseId());
        assertFalse(host.runStatus().hasRun());
        assertTrue("kein autonomer Lauf: " + knowledgeCalls, knowledgeCalls.isEmpty());
    }

    @Test
    public void scenario4to6_wholeWorkflowRunsThroughStripActionsAndTheAgentProcess() throws Exception {
        // Phase 1: Direktrecherche und Folgefrage legen das Konzept an (IAR, IAR/Meilensteine, IAR/Archiv)
        ask(SEARCH_REQUEST);
        ask(FOLLOW_UP);
        assertNotNull("Konzeptkarte IAR", host.existingConceptService().nodeIdAtPath(Arrays.asList("IAR")));
        assertNotNull("Konzeptkarte IAR/Meilensteine", host.existingConceptService()
                .nodeIdAtPath(Arrays.asList("IAR", "Meilensteine")));
        // Weidezaun: der Nutzer schließt ein Thema aus (Wolkenhimmel „+ Hinzufügen“ ruft genau das)
        String rejected = host.addScopeExclusion(EXCLUDED);
        assertEquals(null, rejected);
        assertEquals("scoping", host.phaseId());
        assertFalse("vor dem Klick kein Lauf", host.runStatus().hasRun());

        // Szenario 4: „Recherche starten“ (Tag) → erst jetzt Phase 2, autonomer Lauf aus Konzept + Weidezaun
        int beforeRun = knowledgeCalls.size();
        int entriesBeforeRun = snapshot().size();
        refresh();
        assertEquals(Arrays.asList("Recherche starten"), onEdt(() -> workflow.strip().actionLabels()));
        perform(ResearchWorkflow.START_RESEARCH);
        awaitIdle();
        assertEquals(Arrays.asList("Recherche starten"), sentActions);
        assertEquals("research", host.phaseId());
        assertEquals("waiting_approval", host.stateId());
        assertTrue(host.runStatus().hasRun());
        List<String> run = new ArrayList<String>(knowledgeCalls.subList(beforeRun, knowledgeCalls.size()));
        assertTrue("Suchen aus dem Konzept: " + run, run.contains("knowledge_search IAR Meilensteine"));
        for (String call : run) {
            expected.checkThat("Weidezaun: kein ausgeschlossenes Thema suchen oder lesen: " + run,
                    call.contains(EXCLUDED) || call.endsWith(PAGES[4][0]), is(false));
        }
        expected.checkThat("Weidezaun: ausgeschlossene Seite nicht übernommen", sourceIds().contains(PAGES[4][0]),
                is(false));
        List<TranscriptEntry> runTurn = snapshot();
        runTurn = runTurn.subList(entriesBeforeRun, runTurn.size());
        assertFalse("sichtbare Suche im Lauf", activities(runTurn, "Suche in den Wissensquellen").isEmpty());
        assertTrue(lastAnswer(runTurn), lastAnswer(runTurn).contains("Die Quellensuche ist abgeschlossen"));

        // Szenario 5: „Quellen freigeben“ → Phase 3, der Agent legt die Gliederung vor
        refresh();
        assertEquals(Arrays.asList("Quellen freigeben", "Weiter recherchieren"),
                onEdt(() -> workflow.strip().actionLabels()));
        perform(ResearchWorkflow.APPROVE_EVIDENCE);
        awaitIdle();
        assertEquals("outline", host.phaseId());
        assertEquals("waiting_approval", host.stateId());
        assertTrue(host.outline(), host.outline().contains("## 2. Planung und Meilensteine"));
        List<TranscriptEntry> afterOutline = snapshot();
        assertTrue(lastAnswer(afterOutline), lastAnswer(afterOutline).contains("Vorschlag für die Gliederung"));
        assertNotNull(activity(afterOutline, "Gliederung vorlegen"));
        assertTrue("Gliederung aus Konzept und Weidezaun", script.outlineRequest.contains("KONZEPT:")
                && script.outlineRequest.contains("IAR") && script.outlineRequest.contains(EXCLUDED));

        // Szenario 6: „Gliederung freigeben“ → Phase 4, Kapitel nacheinander, Dokument persistiert
        refresh();
        assertEquals(Arrays.asList("Gliederung freigeben", "Ändern"), onEdt(() -> workflow.strip().actionLabels()));
        int entriesBeforeDraft = snapshot().size();
        perform(ResearchWorkflow.APPROVE_OUTLINE);
        awaitIdle();
        assertEquals("draft", host.phaseId());
        assertEquals("waiting_approval", host.stateId());
        List<TranscriptEntry> draft = snapshot();
        String draftText = allAnswers(draft.subList(entriesBeforeDraft, draft.size()));
        int first = draftText.indexOf("Schreibe Abschnitt 1 von 3");
        int second = draftText.indexOf("Schreibe Abschnitt 2 von 3");
        int third = draftText.indexOf("Schreibe Abschnitt 3 von 3");
        assertTrue(draftText, first >= 0 && first < second && second < third);
        assertEquals("Kapitel nacheinander geschrieben", Arrays.asList("Was ist IAR", "Planung und Meilensteine"),
                script.sectionsWritten);
        ResearchDocument document = host.document();
        assertNotNull(document);
        assertEquals("IAR", document.title());
        assertEquals(3, document.sections().size());
        assertFalse("belegte Aussagen", document.claims().isEmpty());
        assertTrue(document.toMarkdown(), document.toMarkdown().contains("Zuständig ist das Team Auswertung"));
        // Kapitel-Persistenz: während Kapitel 2 entsteht, ist Kapitel 1 schon im Projekt gespeichert
        expected.checkThat("Kapitel-Persistenz: Kapitel 1 ist gespeichert, bevor Kapitel 2 entsteht",
                script.persistedBeforeSecondSection, is(Integer.valueOf(1)));

        // persistiert: ein neuer Host auf demselben Verzeichnis liest dasselbe Gesamtdokument
        ResearchAgentHost restarted = newHost(projects);
        assertEquals("draft", restarted.phaseId());
        assertNotNull(restarted.document());
        assertEquals(document.toMarkdown(), restarted.document().toMarkdown());
        refresh();
        assertEquals(Arrays.asList("Dokument abschließen", "Dokument überarbeiten"),
                onEdt(() -> workflow.strip().actionLabels()));
        assertNoMojibake(snapshot());
    }

    // ------------------------------------------------------------------ Szenario 9

    @Test
    public void scenario9_unicodeSurvivesTheWholeAgentProcessInBothDirections() throws Exception {
        String question = "Was steht zu IAR und " + UNICODE + "?";
        List<TranscriptEntry> entries = ask(question);

        // Host → ACP → Agent → MCP → Modell: Frage und Seitentext kommen unverändert beim Modell an
        assertTrue(script.users.toString(), anyContains(script.users, "NACHRICHT: " + question));
        assertTrue(script.users.toString(), anyContains(script.users, "FRAGE: " + question));
        assertTrue(script.users.toString(), anyContains(script.users, "Sonderzeichen im Projekt: " + UNICODE));
        // Agent → MCP → Quelle: die Suchanfrage mit Sonderzeichen erreicht knowledge_search unverändert
        assertTrue(knowledgeCalls.toString(), knowledgeCalls.contains("knowledge_search " + UNICODE));

        // Modell → MCP → Agent → ACP → Verlauf: Antwort, Wolken und Recherche-Karte ohne Mojibake
        String answer = lastAnswer(entries);
        assertTrue(answer, answer.contains("Zu „" + UNICODE + "“:"));
        assertTrue(answer, answer.contains("Sonderzeichen im Projekt: " + UNICODE));
        TranscriptEntry card = activity(entries, "Recherche in Confluence");
        assertTrue(cards.texts(card.getActivityKey()).toString(),
                anyContains(cards.texts(card.getActivityKey()), "„" + UNICODE + "“"));
        boolean searchCloud = false;
        for (TranscriptEntry search : activities(entries, "Suche in den Wissensquellen")) {
            searchCloud |= UNICODE.equals(search.getText());
        }
        assertTrue("Suchwolke mit der Anfrage", searchCloud);
        assertTrue(activities(entries, "Lese Seite").get(0).getText(), activities(entries, "Lese Seite").get(0)
                .getText().equals("IAR Übersicht"));
        assertNoMojibake(entries);
        for (String text : cards.all()) {
            assertNoMojibake(text);
        }
    }

    // ------------------------------------------------------------------ Fake-Quelle, Korpus, Modell

    private List<McpToolContribution> fakeSource() {
        McpToolContribution search = McpToolContribution.of("knowledge_search", "Volltextsuche", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                String query = call.getString("query");
                knowledgeCalls.add("knowledge_search " + query);
                StringBuilder hits = new StringBuilder();
                for (int i = 0; i < PAGES.length; i++) {
                    hits.append('[').append(i + 1).append("] ").append(PAGES[i][1]).append("\nid: ")
                            .append(PAGES[i][0]).append("\nurl: \nTreffer zu ").append(PAGES[i][1]).append("\n\n");
                }
                return McpToolResult.ok(hits.toString().trim());
            }
        }, McpToolParameter.string("query", true, "Suchbegriffe"), McpToolParameter.integer("limit", false, "max"));
        McpToolContribution read = McpToolContribution.of("knowledge_read", "Seite lesen", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                String id = call.getString("id");
                knowledgeCalls.add("knowledge_read " + id);
                for (String[] page : PAGES) {
                    if (page[0].equals(id)) {
                        readIds.add(id);
                        return McpToolResult.ok(page[1] + "\n\n" + page[2]);
                    }
                }
                return McpToolResult.error("unknown id " + id);
            }
        }, McpToolParameter.string("id", true, "Dokument-ID"));
        return Arrays.asList(search, read);
    }

    /** Der Wissensindex des Projekts: Passagen nur der Seiten, die gelesen (und damit indexiert) wurden. */
    private String corpusSearch(List<String> resources, String query, int limit) {
        StringBuilder passages = new StringBuilder();
        int count = 0;
        for (String[] page : PAGES) {
            if (count < limit && resources.contains(page[0]) && readIds.contains(page[0])) {
                passages.append("passage: ").append(page[0]).append("@cap1#p0@aaaaaaaa-bbbbbbbb\nresource: ")
                        .append(page[0]).append("\ntitle: ").append(page[1]).append('\n')
                        .append(page[2].replace("\n\n", " ")).append("\n\n");
                count++;
            }
        }
        return passages.toString();
    }

    /**
     * Das Hauptmodell als Skript. Antworten werden aus den nummerierten Auszügen gebaut, die der Agent schickt; so
     * belegt ein Fakt in der Antwort, dass die Seite wirklich gelesen wurde.
     */
    static final class ScriptedModel implements ChatCompletionPort {
        private static final Pattern NUMBERED = Pattern.compile("\\[P(\\d+)\\] [^:\\n]+: ([^\\n]+)");
        final List<String> users = new CopyOnWriteArrayList<String>();
        final List<String> sectionsWritten = new CopyOnWriteArrayList<String>();
        volatile String outlineRequest = "";
        volatile Integer persistedBeforeSecondSection;
        private final ResearchAgentHost host;
        private boolean rootCard;
        private boolean childCards;

        ScriptedModel(ResearchAgentHost host) {
            this.host = host;
        }

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            String system = "";
            String user = "";
            for (ChatMessage message : request.messages()) {
                if (message.role() == ChatRole.SYSTEM && system.isEmpty()) {
                    system = message.content();
                } else if (message.role() == ChatRole.USER) {
                    user = message.content();
                }
            }
            users.add(user);
            return new ChatResponse(ChatMessage.assistant(answer(system, user)), ChatFinishReason.STOP,
                    ChatUsage.notReported(), "fake");
        }

        private String answer(String system, String user) {
            if (system.contains("Entscheide, ob die Nachricht")) {
                if (user.contains(UNICODE)) {
                    return "{\"search\": true, \"queries\": [\"IAR\", \"" + UNICODE + "\"]}";
                }
                return "{\"search\": true, \"queries\": [\"IAR\"]}";
            }
            if (system.contains("Beantworte die Frage")) {
                String question = between(user, "FRAGE: ", "\n");
                String prefix = question.contains(UNICODE) ? "Zu „" + UNICODE + "“: " : "";
                return prefix + fromPassages(user, 4);
            }
            if (system.contains("Du erstellst die Gliederung")) {
                outlineRequest = user;
                return "# IAR\n\n## 1. Was ist IAR\n- Zweck\n\n## 2. Planung und Meilensteine\n- Termine\n\n"
                        + "## 3. Offene Punkte und fehlende Informationen\n- Lücken";
            }
            if (system.contains("Du schreibst einen Abschnitt")) {
                String section = between(user, "ABSCHNITT: ", "\n");
                if (sectionsWritten.size() == 1 && persistedBeforeSecondSection == null) {
                    persistedBeforeSecondSection = host.documentDraftSectionCount();
                }
                sectionsWritten.add(section.replaceFirst("^\\d+\\.\\s*", ""));
                return fromPassages(user, 3);
            }
            if (system.contains("Du prüfst einen Rechercheberichts-Entwurf")) {
                return "- Die Quellen nennen keine Kosten.";
            }
            if (!rootCard && user.contains(SEARCH_REQUEST)) {
                rootCard = true;
                return "{\"assistantMessage\":\"Ich lege IAR an.\",\"conceptAction\":{\"type\":\"add_cards\","
                        + "\"parent\":[],\"names\":[\"IAR\"]}}";
            }
            if (rootCard && !childCards && user.contains(FOLLOW_UP)) {
                childCards = true;
                return "{\"assistantMessage\":\"Ich ergänze Meilensteine.\",\"conceptAction\":{\"type\":"
                        + "\"add_cards\",\"parent\":[\"IAR\"],\"names\":[\"Meilensteine\",\"" + EXCLUDED + "\"]}}";
            }
            return "{\"assistantMessage\":\"Notiert.\"}";
        }

        /** Jede Aussage ist der Text eines Auszugs, belegt mit seiner Nummer. */
        private static String fromPassages(String user, int max) {
            StringBuilder text = new StringBuilder();
            Matcher numbered = NUMBERED.matcher(user);
            int count = 0;
            while (numbered.find() && count++ < max) {
                text.append(text.length() == 0 ? "" : " ").append(numbered.group(2).trim()).append(" [P")
                        .append(numbered.group(1)).append(']');
            }
            return text.length() == 0 ? "UNZUREICHEND" : text.toString();
        }

        private static String between(String text, String start, String end) {
            int from = text.indexOf(start);
            if (from < 0) {
                return "";
            }
            from += start.length();
            int to = text.indexOf(end, from);
            return to < 0 ? text.substring(from) : text.substring(from, to);
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            throw new UnsupportedOperationException("the agent asks the host model without streaming");
        }
    }

    /** Schreibt jeden Zwischenstand der Recherche-Karte mit (Text je Hinzufügen und Aktualisieren). */
    static final class CardRecorder implements ChatShellModelListener {
        private final Map<String, List<String>> texts = new LinkedHashMap<String, List<String>>();

        @Override
        public void entryAdded(TranscriptEntry entry) {
            record(entry);
        }

        @Override
        public void entryUpdated(TranscriptEntry entry) {
            record(entry);
        }

        @Override
        public void stateChanged() {
        }

        private synchronized void record(TranscriptEntry entry) {
            if (entry.getAuthor() != TranscriptEntry.Author.ACTIVITY || entry.getActivityKey() == null) {
                return;
            }
            List<String> list = texts.get(entry.getActivityKey());
            if (list == null) {
                list = new ArrayList<String>();
                texts.put(entry.getActivityKey(), list);
            }
            list.add(entry.getActivityTitle() + "\n" + entry.getText() + "\n" + entry.getActivitySummary());
        }

        synchronized List<String> texts(String key) {
            List<String> list = texts.get(key);
            return list == null ? Collections.<String>emptyList() : new ArrayList<String>(list);
        }

        synchronized List<String> all() {
            List<String> all = new ArrayList<String>();
            for (List<String> list : texts.values()) {
                all.addAll(list);
            }
            return all;
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Ein Host für den Chat dieses Tests; ein Neustart bindet wieder an denselben Chat (Projekt je Chat). */
    static ResearchAgentHost newHost(File root) {
        ResearchAgentHost host = new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        host.openChat("chat-e2e");
        return host;
    }

    /** Wie eine Nutzerin im Agent-Modus: Nachricht senden, warten, bis der Agent fertig ist. */
    private List<TranscriptEntry> ask(final String text) throws Exception {
        onEdt(() -> {
            binding.sendRequested(text, false);
            return null;
        });
        awaitIdle();
        return snapshot();
    }

    private void perform(final String action) throws Exception {
        onEdt(() -> {
            workflow.perform(action);
            return null;
        });
        onEdt(() -> null);
    }

    private void refresh() throws Exception {
        onEdt(() -> {
            workflow.refresh();
            return null;
        });
        onEdt(() -> null); // die vom Refresh eingereihte Darstellung abwarten
    }

    private List<String> sourceIds() {
        List<String> ids = new ArrayList<String>();
        for (ResearchSourceRef source : host.sourceRefs()) {
            ids.add(source.resourceId());
        }
        return ids;
    }

    private List<TranscriptEntry> snapshot() throws Exception {
        return onEdt(() -> new ArrayList<TranscriptEntry>(chat.getEntries()));
    }

    private void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(240);
        boolean streaming;
        do {
            Thread.sleep(100L);
            streaming = onEdt(() -> chat.isStreaming());
        } while (streaming && System.nanoTime() < deadline);
        assertFalse("agent turn did not end", streaming);
        onEdt(() -> null);
    }

    static TranscriptEntry activity(List<TranscriptEntry> entries, String title) {
        List<TranscriptEntry> found = activities(entries, title);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        StringBuilder seen = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            seen.append(entry.getAuthor()).append(':').append(entry.getActivityTitle()).append(' ');
        }
        throw new AssertionError("keine Wolke " + title + " in " + seen);
    }

    static List<TranscriptEntry> activities(List<TranscriptEntry> entries, String title) {
        List<TranscriptEntry> found = new ArrayList<TranscriptEntry>();
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY && title.equals(entry.getActivityTitle())) {
                found.add(entry);
            }
        }
        return found;
    }

    static String lastAnswer(List<TranscriptEntry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            TranscriptEntry entry = entries.get(i);
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT && !entry.getText().trim().isEmpty()) {
                return entry.getText();
            }
        }
        return "";
    }

    static String allAnswers(List<TranscriptEntry> entries) {
        StringBuilder text = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                text.append(entry.getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static boolean containsBoth(List<String> texts, String first, String second) {
        for (String text : texts) {
            if (text.contains(first) && text.contains(second)) {
                return true;
            }
        }
        return false;
    }

    static boolean anyContains(List<String> texts, String part) {
        for (String text : texts) {
            if (text != null && text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    static void assertNoNotice(List<TranscriptEntry> entries) {
        for (TranscriptEntry entry : entries) {
            assertFalse("Hinweis im Verlauf: " + entry.getText(), entry.getAuthor() == TranscriptEntry.Author.NOTICE);
            assertFalse("gescheitert: " + entry.getFailureMessage(), entry.getState() == TranscriptEntry.State.FAILED);
        }
    }

    static void assertNoMojibake(List<TranscriptEntry> entries) {
        for (TranscriptEntry entry : entries) {
            assertNoMojibake(entry.getText() + "\n" + entry.getActivityTitle() + "\n" + entry.getActivitySummary());
        }
    }

    /** Typische Spuren falsch dekodierten UTF-8 (CP1252/Latin-1) und Ersatzzeichen. */
    static void assertNoMojibake(String text) {
        for (String broken : new String[]{"Ã", "Â", "â€", "�", "Ã¤", "?? ??"}) {
            assertFalse("Mojibake „" + broken + "“ in: " + text, text != null && text.contains(broken));
        }
    }

    interface EdtCall<T> {
        T call() throws Exception;
    }

    static <T> T onEdt(final EdtCall<T> call) throws Exception {
        final AtomicReference<T> result = new AtomicReference<T>();
        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(call.call());
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return result.get();
    }
}
