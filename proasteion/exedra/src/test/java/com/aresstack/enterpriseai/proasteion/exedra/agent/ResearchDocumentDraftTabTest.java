package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchWorkflow;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Der Reiter „Dokument“ zeigt nach einem abgebrochenen Schreiblauf die schon gespeicherten Kapitel. */
public class ResearchDocumentDraftTabTest {

    private static final String PAGE = "confluence:wiki/page/1001";
    private static final String PASSAGE = PAGE + "@cap1#p0@aaaaaaaa-bbbbbbbb";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theDocumentTabShowsTheSavedChaptersAndHowToContinue() throws Exception {
        final ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"),
                FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        recordMission(host);
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_SOURCES));
        assertNull(host.startAutonomousResearch());
        host.acceptSource(PAGE, "IAR Übersicht", "");
        host.reportRunFinished("SUFFICIENT_EVIDENCE", 1, 1);
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_OUTLINE));
        assertNull(host.proposeOutline("# IAR\n\n## 1. Zweck\n- Wozu\n\n## 2. Planung\n- Wann\n\n## 3. Beteiligte\n"));
        assertNull(host.selectPhase(ResearchWorkflow.PHASE_DOCUMENT));
        assertNull(host.saveDocumentSection(0, "1. Zweck", "IAR bündelt die Auswertung der Anträge. [[" + PASSAGE
                + "]]", "{\"" + PASSAGE + "\":\"" + PAGE + "\"}"));

        final ChatShellModel chat = onEdt(() -> new ChatShellModel(System::currentTimeMillis));
        final ResearchWorkflowAssembly.Parts parts = onEdt(() -> ResearchWorkflowAssembly.create(host,
                (visible, prompt) -> { }, chat, SwingUtilities::invokeLater, (id, title) -> { }));
        onEdt(() -> {
            parts.refresh();
            return null;
        });
        onEdt(() -> null);

        String markdown = onEdt(() -> parts.document().markdown());
        assertTrue(markdown, markdown.contains("## 1. Zweck"));
        assertTrue(markdown, markdown.contains("Auswertung der Anträge. [1]"));
        String line = onEdt(() -> parts.document().statusLine());
        assertTrue(line, line.contains("1 von 3 Kapiteln gespeichert"));
        assertTrue(line, line.contains("„Dokument schreiben“"));
        assertTrue(onEdt(() -> parts.play(ResearchWorkflow.PHASE_DOCUMENT).button().isEnabled()));
    }

    /** Der Auftrag wie aus der ersten Nutzernachricht (research_turn_begin). */
    private static void recordMission(ResearchAgentHost host) {
        for (com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution tool
                : com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools.toolsFor(host,
                new com.aresstack.enterpriseai.research.runtime.team.MainModelChat() {
                    @Override
                    public com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult complete(
                            List<com.aresstack.enterpriseai.research.runtime.team.ChatMessage> messages,
                            double temperature, int maxOutputTokens) {
                        return com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult.ok("");
                    }

                    @Override
                    public String modelName() {
                        return "fake";
                    }
                })) {
            if ("research_turn_begin".equals(tool.getName())) {
                java.util.Map<String, Object> args = new java.util.HashMap<String, Object>();
                args.put("text", "IAR Einführung");
                tool.getHandler().invoke(new com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall(
                        "research_turn_begin", args));
            }
        }
    }

    private static <T> T onEdt(final Callable<T> call) throws Exception {
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
}
