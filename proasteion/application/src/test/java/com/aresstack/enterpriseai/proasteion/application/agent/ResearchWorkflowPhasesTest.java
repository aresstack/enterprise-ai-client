package com.aresstack.enterpriseai.proasteion.application.agent;

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

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Phasenlogik des Research-Workflows im Host: nur der Nutzer wählt die Phase (beliebige Reihenfolge), jede
 * Play-Aktion arbeitet nur in ihrer Phase und wechselt sie nie, der Agent ändert sie nicht, der autonome Lauf startet
 * nur mit Auftrag, Quellen kommen in jeder Phase ins Projekt, und das Dokument wird Kapitel für Kapitel gespeichert.
 */
public class ResearchWorkflowPhasesTest {

    private static final String PAGE = "confluence:wiki/page/1001";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** Jeder Host gehört zu einem Chat; ein Neustart bindet wieder an denselben Chat. */
    private static ResearchAgentHost host(File root) {
        ResearchAgentHost host = new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        host.openChat("chat-iar");
        return host;
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

    @Test
    public void startingResearchWithoutABriefKeepsThePhaseWithAHint() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("r"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        assertEquals("Phase ‚Quellen‘ auswählen, um die Recherche zu starten.",
                workflow.perform(ResearchWorkflow.PHASE_SOURCES).rejection());
        assertEquals("scoping", host.phaseId());

        assertNull(workflow.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        ResearchWorkflow.Result result = workflow.perform(ResearchWorkflow.PHASE_SOURCES);
        assertTrue(String.valueOf(result.rejection()), result.rejection().contains("fehlt noch ein Auftrag"));
        assertEquals("research", host.phaseId());

        // eine bloße Begrüßung ist kein Auftrag; der Textbefehl wird ebenso abgelehnt
        call(host, "research_turn_begin", "text", "Hallo");
        String text = call(host, "research_turn_begin", "text", "Recherche starten").getText();
        assertTrue(text, text.contains("fehlt noch ein Auftrag"));
        assertEquals("research", host.phaseId());
    }

    @Test
    public void aRecordedBriefStartsTheRunOnlyInTheSourcePhaseAndNeverSwitchesIt() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("r"));
        call(host, "research_turn_begin", "text", "Was ist IAR und wer betreut es?");
        assertNull(new ResearchWorkflow(host).selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertNull(new ResearchWorkflow(host).perform(ResearchWorkflow.PHASE_SOURCES).rejection());
        assertEquals("research", host.phaseId());
        assertEquals("running", host.stateId());

        // der Präfix im Konzept wechselt die Phase nicht (nur der Nutzer wählt sie)
        ResearchAgentHost prefixed = host(folder.newFolder("p"));
        call(prefixed, "research_turn_begin", "text", "research: IAR Einführung");
        assertEquals("scoping", prefixed.phaseId());
        assertNull(prefixed.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertEquals("", call(prefixed, "research_turn_begin", "text", "research: IAR Einführung").getText());
        assertEquals("research/running", prefixed.phaseId() + "/" + prefixed.stateId());
    }

    @Test
    public void theUserPicksPhasesInAnyOrderAndThePlayOfAPhaseIsOnlyActiveThere() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("any"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        assertEquals(4, workflow.phaseChoices().size());
        assertNull(workflow.play(ResearchWorkflow.PHASE_CONCEPT));
        for (String phase : new String[]{ResearchWorkflow.PHASE_DOCUMENT, ResearchWorkflow.PHASE_CONCEPT,
                ResearchWorkflow.PHASE_OUTLINE, ResearchWorkflow.PHASE_SOURCES}) {
            assertNull(workflow.selectPhase(phase));
            assertEquals(phase, workflow.currentPhaseId());
            for (String other : new String[]{ResearchWorkflow.PHASE_SOURCES, ResearchWorkflow.PHASE_OUTLINE,
                    ResearchWorkflow.PHASE_DOCUMENT}) {
                ResearchWorkflow.Play play = workflow.play(other);
                assertEquals(phase + "/" + other, phase.equals(other), play.isEnabled());
                if (!phase.equals(other)) {
                    assertTrue(play.tooltip(), play.tooltip().startsWith("Phase ‚"));
                    assertEquals(play.tooltip(), workflow.perform(other).rejection());
                    assertEquals("eine fremde Play-Aktion wechselt die Phase nicht", phase, host.phaseId());
                }
            }
        }
        assertEquals("Phase ‚Dokument‘ auswählen, um das Dokument zu schreiben.",
                workflow.play(ResearchWorkflow.PHASE_DOCUMENT).tooltip());
        assertEquals("Recherche starten", workflow.play(ResearchWorkflow.PHASE_SOURCES).tooltip());
        assertTrue(workflow.selectPhase("unbekannt").startsWith("Unbekannte Phase"));
        assertEquals(ResearchWorkflow.PHASE_SOURCES, host.phaseId());
    }

    @Test
    public void eachPlayStartsOnlyItsOwnActionAndKeepsThePhase() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("play"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        call(host, "research_turn_begin", "text", "IAR Einführung");

        assertNull(workflow.selectPhase(ResearchWorkflow.PHASE_OUTLINE));
        ResearchWorkflow.Result outline = workflow.perform(ResearchWorkflow.PHASE_OUTLINE);
        assertNull(outline.rejection());
        assertEquals("Gliederung erstellen", outline.prompt());
        assertEquals("outline/running", host.phaseId() + "/" + host.stateId());

        assertNull(workflow.selectPhase(ResearchWorkflow.PHASE_DOCUMENT));
        ResearchWorkflow.Result document = workflow.perform(ResearchWorkflow.PHASE_DOCUMENT);
        assertEquals("Dokument schreiben", document.prompt());
        assertEquals("draft/running", host.phaseId() + "/" + host.stateId());

        assertNull(workflow.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        ResearchWorkflow.Result research = workflow.perform(ResearchWorkflow.PHASE_SOURCES);
        assertEquals("Recherche starten", research.prompt());
        assertEquals("research/running", host.phaseId() + "/" + host.stateId());
    }

    @Test
    public void theAgentCannotChangeThePhase() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("agent"));
        // was über die Werkzeuge des Agenten läuft, bleibt in der Phase: Auftrag, Startbefehle, Lauf-Ende
        call(host, "research_turn_begin", "text", "Recherche starten: IAR Einführung");
        call(host, "research_turn_begin", "text", "Gliederung erstellen");
        call(host, "research_turn_begin", "text", "Dokument schreiben");
        assertEquals("scoping", host.phaseId());

        assertNull(host.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        host.reportRunFinished("SUFFICIENT_EVIDENCE", 1, 1);
        assertEquals("research", host.phaseId());
        call(host, "research_turn_begin", "text", "Gliederung erstellen");
        assertEquals("research", host.phaseId());
    }

    @Test
    public void whileTheOutlineWaitsAQuestionIsNoChangeRequestButAChangeRequestIs() throws Exception {
        ResearchAgentHost host = outlineWaitingIn(folder.newFolder("o"));
        assertEquals("outline/waiting_approval", host.phaseId() + "/" + host.stateId());
        call(host, "research_turn_begin", "text", "Welche Meilensteine hat IAR?");
        assertEquals("waiting_approval", host.stateId());
        call(host, "research_turn_begin", "text", "Ergänze ein Kapitel zu den Kosten.");
        assertEquals("outline/running", host.phaseId() + "/" + host.stateId());
    }

    @Test
    public void followUpSourcesAreAcceptedInEveryPhase() throws Exception {
        ResearchAgentHost host = outlineWaitingIn(folder.newFolder("f"));
        McpToolResult added = call(host, "project_source_add", "capture_id", "confluence:wiki/page/1002",
                "title", "IAR Planung", "url", "");
        assertFalse(added.getText(), added.isError());
        assertEquals(2, host.sourceRefs().size());

        ResearchAgentHost running = host(folder.newFolder("rr"));
        call(running, "research_turn_begin", "text", "IAR Einführung");
        assertNull(running.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertNull(running.startAutonomousResearch());
        assertFalse(call(running, "project_source_add", "capture_id", PAGE, "title", "IAR", "url", "").isError());
    }

    @Test
    public void theStopReasonDecidesAboutTheRunStateButNeverAboutThePhase() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("s"));
        ResearchWorkflow workflow = new ResearchWorkflow(host);
        call(host, "research_turn_begin", "text", "IAR Einführung");
        assertNull(workflow.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertNull(host.startAutonomousResearch());

        String failed = host.reportRunFinished("MCP_UNAVAILABLE", 0, 0);
        assertEquals("ein Fehler geht nicht in die Freigabe", "research/running", host.phaseId() + "/" + host.stateId());
        assertTrue(failed, failed.contains("fehlgeschlagen"));

        host.reportRunFinished("USER_CANCELLED", 2, 0);
        assertEquals("ein Abbruch pausiert", "research/paused", host.phaseId() + "/" + host.stateId());
        assertTrue(workflow.play(ResearchWorkflow.PHASE_SOURCES).isEnabled());
        assertNull(workflow.perform(ResearchWorkflow.PHASE_SOURCES).rejection());
        assertEquals("running", host.stateId());

        host.reportRunFinished("PAGE_BUDGET_EXHAUSTED", 4, 1);
        assertEquals("research/waiting_approval", host.phaseId() + "/" + host.stateId());
        assertNull("nach dem Lauf startet Play erneut", workflow.perform(ResearchWorkflow.PHASE_SOURCES).rejection());
        assertEquals("research/running", host.phaseId() + "/" + host.stateId());
    }

    @Test
    public void documentChaptersArePersistedOneByOneAndSurviveARestart() throws Exception {
        File root = folder.newFolder("d");
        ResearchAgentHost host = outlineWaitingIn(root);
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_DOCUMENT));
        assertEquals("draft/running", host.phaseId() + "/" + host.stateId());

        assertFalse(call(host, "document_section_save", "index", 0L, "heading", "1. Zweck", "text",
                "IAR bündelt die Auswertung.", "passages_json", "{}").isError());
        // ein Fehler bei Kapitel 2: Kapitel 1 bleibt, auch über einen Neustart
        ResearchAgentHost restarted = host(root);
        assertEquals(1, restarted.documentDraftSectionCount());
        assertTrue(restarted.documentDraftJson(), restarted.documentDraftJson().contains("IAR bündelt"));
        ResearchWorkflow workflow = new ResearchWorkflow(restarted);
        assertTrue(workflow.play(ResearchWorkflow.PHASE_DOCUMENT).isEnabled());
        assertTrue(workflow.documentDraftLine(), workflow.documentDraftLine().contains("„Dokument schreiben“"));
        assertEquals("Dokument schreiben", workflow.perform(ResearchWorkflow.PHASE_DOCUMENT).visibleText());

        assertTrue("Lücken werden abgelehnt", call(restarted, "document_section_save", "index", 3L, "heading", "x",
                "text", "y").isError());
        assertFalse(call(restarted, "document_section_save", "index", 1L, "heading", "2. Planung", "text",
                "Pilot im Frühjahr.").isError());
        assertEquals(2, restarted.documentDraftSectionCount());
        assertFalse(call(restarted, "document_save", "document_json", "{\"title\":\"IAR\",\"sections\":["
                + "{\"heading\":\"1. Zweck\",\"text\":\"IAR bündelt die Auswertung.\"},"
                + "{\"heading\":\"2. Planung\",\"text\":\"Pilot im Frühjahr.\"}],\"passages\":{}}").isError());
        assertEquals("das fertige Dokument ersetzt den Zwischenstand", 0, restarted.documentDraftSectionCount());
        assertEquals("draft", restarted.phaseId());
    }

    /** Bringt ein Projekt in die Phase Gliederung mit vorgelegter Gliederung. */
    private ResearchAgentHost outlineWaitingIn(File root) {
        ResearchAgentHost host = host(root);
        call(host, "research_turn_begin", "text", "IAR Einführung");
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertNull(host.startAutonomousResearch());
        host.acceptSource(PAGE, "IAR Übersicht", "");
        host.reportRunFinished("SUFFICIENT_EVIDENCE", 1, 1);
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_OUTLINE));
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
