package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die zentrale Werkzeug-Sichtbarkeit des Recherche-Agenten (askai arch {@code ResearchToolPolicy}): Schreib- und
 * Laufwerkzeuge nur in ihrer Phase und ihrem Zustand, die Pflichtwerkzeuge immer.
 */
public class ResearchToolPolicyTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writeAndRunToolsFollowPhaseAndStateRequiredToolsStay() throws Exception {
        ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"), FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));

        // Konzept: direkter Suchauftrag darf Quellen notieren, sonst kein Schreib- oder Laufwerkzeug.
        List<String> scoping = names(host);
        assertRequired(scoping);
        assertTrue(scoping.contains("project_source_add"));
        assertFalse(scoping.contains("outline_propose"));
        assertFalse(scoping.contains("document_save"));
        assertFalse(scoping.contains("research_progress"));
        assertFalse(scoping.contains("research_run_finished"));

        host.markGreeted();
        assertNull(host.dispatch(ResearchCommandType.SUBMIT_SCOPE));
        assertNull(host.dispatch(ResearchCommandType.START_RESEARCH));
        List<String> running = names(host);
        assertRequired(running);
        assertTrue(running.contains("research_progress"));
        assertTrue(running.contains("research_run_finished"));
        assertFalse(running.contains("project_source_add"));
        assertFalse(running.contains("outline_propose"));

        assertNull(host.dispatch(ResearchCommandType.REQUEST_EVIDENCE_REVIEW));
        List<String> approval = names(host);
        assertRequired(approval);
        assertFalse("Lauf vorbei", approval.contains("research_progress"));
        assertFalse(approval.contains("research_run_finished"));

        assertNull(host.dispatch(ResearchCommandType.APPROVE_EVIDENCE));
        assertEquals("outline", host.phaseId());
        List<String> outline = names(host);
        assertRequired(outline);
        assertTrue(outline.contains("outline_propose"));
        assertFalse(outline.contains("document_save"));
    }

    @Test
    public void controlToolsAreRequiredUserToolsAreNot() {
        assertTrue(ResearchControlTools.isRequired("research_status"));
        assertTrue(ResearchControlTools.isRequired("main_model_complete"));
        assertTrue(ResearchControlTools.isRequired("research_turn_begin"));
        assertFalse(ResearchControlTools.isRequired("knowledge_search"));
        assertFalse(ResearchControlTools.isRequired("concept_read"));
        assertFalse(ResearchControlTools.isRequired("source_accept"));
        assertFalse(ResearchControlTools.isRequired(null));
    }

    private static void assertRequired(List<String> names) {
        assertTrue(names.toString(), names.contains("research_status"));
        assertTrue(names.toString(), names.contains("main_model_complete"));
        assertTrue(names.toString(), names.contains("research_turn_begin"));
    }

    private static List<String> names(ResearchAgentHost host) {
        List<String> names = new ArrayList<String>();
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
            names.add(tool.getName());
        }
        return names;
    }
}
