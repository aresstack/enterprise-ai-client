package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchWorkflow;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Abnahmeszenario des geführten Recherche-Workflows ohne Textbefehle: jeder Phasenwechsel kommt aus einem
 * Aktions-Tag der Oberfläche, jeder Agentenschritt aus den MCP-Werkzeugen des Hosts (wie der Agentenprozess sie
 * ruft) über eine Fake-Quelle (Projektkorpus) ohne echtes Modell. Am Ende überlebt das Projekt einen Neustart.
 */
public class ResearchWorkflowAcceptanceTest {

    private static final String PAGE = "confluence:wiki/page/1001";
    private static final String MORE = "confluence:wiki/page/1002";
    private static final String PASSAGE = PAGE + "@cap1#p0@aaaaaaaa-bbbbbbbb";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final List<String> prompts = new ArrayList<String>();
    private final List<String> opened = new ArrayList<String>();
    private final List<String> tabs = new ArrayList<String>();

    @Test
    public void wholeResearchFlowRunsThroughUiActionsAndHostTransitions() throws Exception {
        File root = folder.newFolder("research");
        final ResearchAgentHost host = host(root);
        host.setCorpusSearch((resources, query, limit) -> resources.contains(PAGE)
                ? "passage: " + PASSAGE + "\nresource: " + PAGE + "\ntitle: IAR Übersicht\n"
                + "Überblick: IAR bündelt die Auswertung der Anträge.\n\n" : "");
        final ChatShellModel chat = onEdt(() -> new ChatShellModel(System::currentTimeMillis));
        final Executor ui = SwingUtilities::invokeLater;
        final ResearchWorkflowAssembly.Parts parts = onEdt(() -> ResearchWorkflowAssembly.create(host,
                (visible, prompt) -> prompts.add(prompt), chat, ui,
                (resourceId, title) -> opened.add(resourceId), tabs::add));

        // Konzept: die Leiste zeigt alle vier Phasen, aktiv ist Konzept, der Start ist ein Tag
        refresh(parts);
        assertEquals(Arrays.asList("Recherche starten"), onEdt(() -> parts.strip().actionLabels()));

        // Ohne Auftrag bleibt es beim Konzept: kein Phasenwechsel, kein Auftrag an den Agenten, ein Hinweis
        perform(parts, ResearchWorkflow.START_RESEARCH);
        assertEquals("scoping", host.phaseId());
        assertTrue(prompts.isEmpty());
        String notices = onEdt(() -> {
            StringBuilder text = new StringBuilder();
            for (com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry entry : chat.getEntries()) {
                text.append(entry.getText()).append('\n');
            }
            return text.toString();
        });
        assertTrue(notices, notices.contains("fehlt noch ein Auftrag"));

        // Phase 1 bleibt ein Recherche-Chat: die erste Frage wird zum Auftrag
        call(host, "research_turn_begin", "text", "Was ist IAR und wer betreut es?");

        // Recherche starten (Tag) → SUBMIT_SCOPE, START_RESEARCH → research/running, Auftrag an den Agenten
        perform(parts, ResearchWorkflow.START_RESEARCH);
        assertEquals("research", host.phaseId());
        assertEquals("running", host.stateId());
        assertEquals("Recherche starten", last(prompts));
        assertEquals("Quellen", last(tabs));

        // Agent: Fortschritt, Seite lesen, Quelle übernehmen, Lauf beenden
        call(host, "research_progress", "activity", "SEARCHING", "query", "IAR", "pages", "0", "accepted", "0");
        call(host, "research_progress", "activity", "READING_PAGE", "query", "IAR", "title", "IAR Übersicht",
                "pages", "1", "accepted", "0");
        call(host, "source_accept", "capture_id", PAGE, "title", "IAR Übersicht", "url", "");
        call(host, "research_progress", "activity", "SOURCE_ACCEPTED", "title", "IAR Übersicht", "pages", "1",
                "accepted", "1");
        refresh(parts);
        assertTrue(onEdt(() -> parts.strip().progressText()), onEdt(() -> parts.strip().progressText())
                .contains("1 Quelle übernommen"));
        String summary = call(host, "research_run_finished", "reason", "TARGET_REACHED", "pages", "1",
                "accepted", "1");
        assertTrue(summary, summary.contains("IAR Übersicht"));
        assertEquals("waiting_approval", host.stateId());
        refresh(parts);
        assertEquals(1, (int) onEdt(() -> parts.sources().sourceCount()));
        assertEquals(Arrays.asList("Quellen freigeben", "Weiter recherchieren"),
                onEdt(() -> parts.strip().actionLabels()));
        onEdt(() -> {
            parts.sources().open(0);
            return null;
        });
        assertEquals(PAGE, last(opened));

        // Quellen freigeben → outline/running, der Agent legt die Gliederung vor
        perform(parts, ResearchWorkflow.APPROVE_EVIDENCE);
        assertEquals("outline", host.phaseId());
        assertEquals("Gliederung erstellen", last(prompts));
        call(host, "outline_propose", "outline", "# IAR\n\n## 1. Was ist IAR\n- Zweck\n\n## 2. Offene Punkte\n");
        assertEquals("waiting_approval", host.stateId());
        refresh(parts);
        assertEquals(Arrays.asList("Gliederung freigeben", "Ändern"), onEdt(() -> parts.strip().actionLabels()));

        // Ändern → Reiter Gliederung im Textmodus, Korrektur speichern
        perform(parts, ResearchWorkflow.CHANGE_OUTLINE);
        assertEquals("Gliederung", last(tabs));
        assertTrue(onEdt(() -> parts.outline().isEditing()));
        onEdt(() -> {
            parts.outline().setEditorText("# IAR\n\n## 1. Was ist IAR\n- Zweck\n- Ziele\n\n## 2. Offene Punkte\n");
            parts.outline().save();
            return null;
        });
        assertTrue(host.outline().contains("- Ziele"));

        // Gliederung freigeben → APPROVE_OUTLINE, START_DRAFTING → draft/running
        perform(parts, ResearchWorkflow.APPROVE_OUTLINE);
        assertEquals("draft", host.phaseId());
        assertEquals("running", host.stateId());
        assertEquals("Dokument erstellen", last(prompts));

        // Agent: Projektsuche über die übernommenen Quellen, Dokument mit Passagen-Beleg speichern
        String found = call(host, "project_search", "query", "Was ist IAR", "limit", "8");
        assertTrue(found, found.contains(PASSAGE));
        call(host, "document_save", "document_json", "{\"title\":\"IAR\",\"sections\":[{\"heading\":"
                + "\"Was ist IAR\",\"text\":\"IAR bündelt die Auswertung der Anträge. [[" + PASSAGE + "]]\"}],"
                + "\"passages\":{\"" + PASSAGE + "\":\"" + PAGE + "\"}}");
        assertEquals("waiting_approval", host.stateId());
        refresh(parts);
        String document = onEdt(() -> parts.document().markdown());
        assertTrue(document, document.contains("Anträge. [1]"));
        assertEquals(1, host.document().claims().size());
        onEdt(() -> {
            parts.document().openSource(1);
            return null;
        });
        assertEquals(PAGE, last(opened));

        // Nachfrage: Projektkorpus reicht nicht → weitere Seite ins Projekt
        call(host, "project_source_add", "capture_id", MORE, "title", "IAR Planung", "url", "");
        refresh(parts);
        assertEquals(2, (int) onEdt(() -> parts.sources().sourceCount()));

        // Dokument abschließen
        assertEquals(Arrays.asList("Dokument abschließen", "Dokument überarbeiten"),
                onEdt(() -> parts.strip().actionLabels()));
        perform(parts, ResearchWorkflow.FINISH_DOCUMENT);
        assertEquals("completed", host.stateId());

        // Neustart: dasselbe Projekt mit Zustand, Quellen, Gliederung und Dokument
        ResearchAgentHost restarted = host(root);
        assertEquals("draft", restarted.phaseId());
        assertEquals("completed", restarted.stateId());
        assertEquals(2, restarted.sourceRefs().size());
        assertTrue(restarted.outline().contains("- Ziele"));
        assertEquals("IAR", restarted.document().title());
        assertFalse(restarted.runStatus().isActive());
        assertTrue(restarted.runStatus().hasRun());
    }

    private static ResearchAgentHost host(File root) {
        return new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
    }

    private static Map<String, McpToolContribution> tools(ResearchAgentHost host) {
        Map<String, McpToolContribution> tools = new LinkedHashMap<String, McpToolContribution>();
        for (McpToolContribution tool : ResearchControlTools.toolsFor(host, new MainModelChat() {
            @Override
            public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
                return MainModelChatResult.ok("");
            }

            @Override
            public String modelName() {
                return "fake";
            }
        })) {
            tools.put(tool.getName(), tool);
        }
        return tools;
    }

    /** Die Werkzeuge der aktuellen Phase (wie PhaseToolSetController sie dem Agenten nach jedem Wechsel gibt). */
    private static String call(ResearchAgentHost host, String name, String... pairs) {
        Map<String, McpToolContribution> tools = tools(host);
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            args.put(pairs[i], pairs[i + 1]);
        }
        McpToolContribution tool = tools.get(name);
        assertTrue("tool " + name + " registered", tool != null);
        McpToolResult result = tool.getHandler().invoke(new McpToolCall(name, args));
        assertFalse(name + ": " + result.getText(), result.isError());
        return result.getText();
    }

    private static void perform(final ResearchWorkflowAssembly.Parts parts, final String action) throws Exception {
        onEdt(() -> {
            parts.perform(action);
            return null;
        });
        refresh(parts);
    }

    private static void refresh(final ResearchWorkflowAssembly.Parts parts) throws Exception {
        onEdt(() -> {
            parts.refresh();
            return null;
        });
        onEdt(() -> null); // die vom Refresh eingereihte Darstellung abwarten
    }

    private interface EdtCall<T> {
        T call() throws Exception;
    }

    private static <T> T onEdt(final EdtCall<T> call) throws Exception {
        final List<Object> result = new ArrayList<Object>(1);
        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.add(call.call());
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        @SuppressWarnings("unchecked")
        T value = (T) result.get(0);
        return value;
    }

    private static String last(List<String> values) {
        return values.isEmpty() ? "" : values.get(values.size() - 1);
    }
}
