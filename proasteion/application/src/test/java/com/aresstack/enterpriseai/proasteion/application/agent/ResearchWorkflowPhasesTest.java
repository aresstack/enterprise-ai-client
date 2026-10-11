package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Phasen- und Übergangslogik des Research-Workflows im Host: der autonome Lauf startet nur mit Auftrag, Fragen
 * lösen keine Übergänge aus, Quellen kommen in jeder Phase ins Projekt, der Stoppgrund entscheidet über die
 * Freigabe, und das Dokument wird Kapitel für Kapitel gespeichert.
 */
public class ResearchWorkflowPhasesTest {

    private static final String PAGE = "confluence:wiki/page/1001";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static ResearchAgentHost host(File root) {
        return new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
    }

    private static McpToolResult call(ResearchAgentHost host, String name, Object... pairs) {
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
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            args.put((String) pairs[i], pairs[i + 1]);
        }
        assertTrue(name, tools.containsKey(name));
        return tools.get(name).getHandler().invoke(new McpToolCall(name, args));
    }

    private List<String> labels(ResearchWorkflow workflow) {
        List<String> labels = new ArrayList<String>();
        for (ResearchWorkflow.Action action : workflow.actions()) {
            labels.add(action.label());
        }
        return labels;
    }

    @Test
    public void startingResearchWithoutABriefStaysInTheConceptWithAHint() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("r"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        ResearchWorkflow.Result result = workflow.perform(ResearchWorkflow.START_RESEARCH);
        assertTrue(String.valueOf(result.rejection()), result.rejection().contains("fehlt noch ein Auftrag"));
        assertEquals("scoping", host.phaseId());

        // eine bloße Begrüßung ist kein Auftrag; der Textbefehl wird ebenso abgelehnt
        call(host, "research_turn_begin", "text", "Hallo");
        String text = call(host, "research_turn_begin", "text", "Recherche starten").getText();
        assertTrue(text, text.contains("fehlt noch ein Auftrag"));
        assertEquals("scoping", host.phaseId());
    }

    @Test
    public void aRecordedBriefOrTheResearchPrefixStartsTheSourcePhase() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("r"));
        call(host, "research_turn_begin", "text", "Was ist IAR und wer betreut es?");
        assertNull(new ResearchWorkflow(host).perform(ResearchWorkflow.START_RESEARCH).rejection());
        assertEquals("research", host.phaseId());
        assertEquals("running", host.stateId());

        ResearchAgentHost prefixed = host(folder.newFolder("p"));
        assertEquals("", call(prefixed, "research_turn_begin", "text", "research: IAR Einführung").getText());
        assertEquals("research", prefixed.phaseId());
        assertEquals("running", prefixed.stateId());
    }

    /** Bringt ein Projekt in die Phase Gliederung (wartet auf Freigabe). */
    private ResearchAgentHost outlineWaiting(String name) throws Exception {
        ResearchAgentHost host = host(folder.newFolder(name));
        call(host, "research_turn_begin", "text", "IAR Einführung");
        assertNull(host.startAutonomousResearch());
        host.acceptSource(PAGE, "IAR Übersicht", "");
        host.reportRunFinished("SUFFICIENT_EVIDENCE", 1, 1);
        assertNull(host.dispatch(ResearchCommandType.APPROVE_EVIDENCE));
        assertNull(host.proposeOutline("# IAR\n\n## 1. Zweck\n- Wozu\n\n## 2. Planung\n- Wann\n"));
        assertEquals("outline/waiting_approval", host.phaseId() + "/" + host.stateId());
        return host;
    }

    @Test
    public void whileTheOutlineWaitsAQuestionIsNoChangeRequestButAChangeRequestIs() throws Exception {
        ResearchAgentHost host = outlineWaiting("o");
        call(host, "research_turn_begin", "text", "Welche Meilensteine hat IAR?");
        assertEquals("waiting_approval", host.stateId());
        call(host, "research_turn_begin", "text", "Ergänze ein Kapitel zu den Kosten.");
        assertEquals("outline/running", host.phaseId() + "/" + host.stateId());
    }

    @Test
    public void followUpSourcesAreAcceptedInEveryPhase() throws Exception {
        ResearchAgentHost host = outlineWaiting("f");
        McpToolResult added = call(host, "project_source_add", "capture_id", "confluence:wiki/page/1002",
                "title", "IAR Planung", "url", "");
        assertFalse(added.getText(), added.isError());
        assertEquals(2, host.sourceRefs().size());

        ResearchAgentHost running = host(folder.newFolder("rr"));
        call(running, "research_turn_begin", "text", "IAR Einführung");
        assertNull(running.startAutonomousResearch());
        assertFalse(call(running, "project_source_add", "capture_id", PAGE, "title", "IAR", "url", "").isError());
    }

    @Test
    public void theStopReasonDecidesAboutTheEvidenceReview() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("s"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        call(host, "research_turn_begin", "text", "IAR Einführung");
        assertNull(host.startAutonomousResearch());

        String failed = host.reportRunFinished("MCP_UNAVAILABLE", 0, 0);
        assertEquals("ein Fehler geht nicht in die Freigabe", "research/running", host.phaseId() + "/" + host.stateId());
        assertTrue(failed, failed.contains("fehlgeschlagen"));

        host.reportRunFinished("USER_CANCELLED", 2, 0);
        assertEquals("ein Abbruch pausiert", "research/paused", host.phaseId() + "/" + host.stateId());
        assertTrue(labels(workflow).toString(), labels(workflow).contains("Recherche fortsetzen"));
        assertNull(workflow.perform(ResearchWorkflow.START_RESEARCH).rejection());
        assertEquals("running", host.stateId());

        host.reportRunFinished("PAGE_BUDGET_EXHAUSTED", 4, 1);
        assertEquals("waiting_approval", host.stateId());
    }

    @Test
    public void documentChaptersArePersistedOneByOneAndSurviveARestart() throws Exception {
        File root = folder.newFolder("d");
        ResearchAgentHost host = outlineWaitingIn(root);
        assertNull(host.dispatch(ResearchCommandType.APPROVE_OUTLINE));
        assertNull(host.dispatch(ResearchCommandType.START_DRAFTING));
        assertEquals("draft/running", host.phaseId() + "/" + host.stateId());

        assertFalse(call(host, "document_section_save", "index", 0L, "heading", "1. Zweck", "text",
                "IAR bündelt die Auswertung.", "passages_json", "{}").isError());
        // ein Fehler bei Kapitel 2: Kapitel 1 bleibt, auch über einen Neustart
        ResearchAgentHost restarted = host(root);
        assertEquals(1, restarted.documentDraftSectionCount());
        assertTrue(restarted.documentDraftJson(), restarted.documentDraftJson().contains("IAR bündelt"));
        ResearchWorkflow workflow = new ResearchWorkflow(restarted);
        assertTrue(labels(workflow).toString(), labels(workflow).contains("Dokument fortsetzen"));
        assertEquals("Dokument fortsetzen",
                workflow.perform(ResearchWorkflow.APPROVE_OUTLINE).visibleText());

        assertTrue("Lücken werden abgelehnt", call(restarted, "document_section_save", "index", 3L, "heading", "x",
                "text", "y").isError());
        assertFalse(call(restarted, "document_section_save", "index", 1L, "heading", "2. Planung", "text",
                "Pilot im Frühjahr.").isError());
        assertEquals(2, restarted.documentDraftSectionCount());
        assertFalse(call(restarted, "document_save", "document_json", "{\"title\":\"IAR\",\"sections\":["
                + "{\"heading\":\"1. Zweck\",\"text\":\"IAR bündelt die Auswertung.\"},"
                + "{\"heading\":\"2. Planung\",\"text\":\"Pilot im Frühjahr.\"}],\"passages\":{}}").isError());
        assertEquals("das fertige Dokument ersetzt den Zwischenstand", 0, restarted.documentDraftSectionCount());
    }

    private ResearchAgentHost outlineWaitingIn(File root) {
        ResearchAgentHost host = host(root);
        call(host, "research_turn_begin", "text", "IAR Einführung");
        assertNull(host.startAutonomousResearch());
        host.acceptSource(PAGE, "IAR Übersicht", "");
        host.reportRunFinished("SUFFICIENT_EVIDENCE", 1, 1);
        assertNull(host.dispatch(ResearchCommandType.APPROVE_EVIDENCE));
        assertNull(host.proposeOutline("# IAR\n\n## 1. Zweck\n- Wozu\n\n## 2. Planung\n- Wann\n"));
        return host;
    }

    @Test
    public void visitedPagesAreKeptInTheProjectAcrossRestarts() throws Exception {
        File root = folder.newFolder("v");
        ResearchAgentHost host = host(root);
        call(host, "research_turn_begin", "text", "IAR Einführung");
        call(host, "research_visited_add", "urls", "knowledge:" + PAGE + "\nknowledge:" + PAGE);
        ResearchAgentHost restarted = host(root);
        assertEquals("knowledge:" + PAGE, call(restarted, "research_visited").getText());
    }
}
