package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
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

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Die gelben Suchvorschläge des Agenten (offer_searches) sind über dem Composer sichtbar: ein Klick schickt die
 * Direktrecherche genau dieser Anfrage an den Agenten (kein Phasenwechsel, kein Auftrag), danach trägt der Tag den
 * Haken „schon gesucht“ oder nach einem Fehler den Kreispfeil zum Wiederholen.
 */
public class ResearchSearchTagsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void yellowSuggestionsAreVisibleSearchOnClickAndShowCheckOrRetry() throws Exception {
        final ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"),
                FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        host.markGreeted(); // Konzept läuft
        McpToolResult offered = call(host, "offer_searches", "suggestions_json",
                "[{\"query\":\"IAR Betrieb\",\"purpose\":\"Einstieg\"},{\"query\":\"IAR Planung\",\"purpose\":\"\"}]");
        assertFalse(offered.getText(), offered.isError());

        final List<String> prompts = new ArrayList<String>();
        final ChatShellModel chat = onEdt(() -> new ChatShellModel(System::currentTimeMillis));
        final ResearchWorkflowAssembly.Parts parts = onEdt(() -> ResearchWorkflowAssembly.create(host,
                (visible, prompt) -> prompts.add(prompt), chat, SwingUtilities::invokeLater, (id, title) -> { },
                title -> { }));
        refresh(parts);
        assertEquals(Arrays.asList("IAR Betrieb", "IAR Planung"), onEdt(() -> parts.strip().suggestionLabels()));

        // Klick: Direktrecherche genau dieser Anfrage, kein Phasenwechsel, kein Auftrag
        onEdt(() -> {
            parts.strip().suggestionButton("IAR Betrieb").doClick();
            return null;
        });
        refresh(parts);
        assertEquals(Arrays.asList("Wissenssuche: IAR Betrieb"), prompts);
        assertEquals("scoping", host.phaseId());
        onEdt(chat::beginAssistantMessage); // der Agenten-Turn läuft
        refresh(parts);
        assertEquals("IAR Betrieb (sucht …)", onEdt(() -> parts.strip().suggestionLabels()).get(0));
        assertFalse(onEdt(() -> parts.strip().suggestionButton("IAR Betrieb").isEnabled()));
        // der Agent beginnt den Turn mit genau diesem Text: kein Auftrag daraus
        call(host, "research_turn_begin", "text", "Wissenssuche: IAR Betrieb");
        assertNotNull(host.researchBriefGap());

        // Erfolg: Haken und Vermerk, nicht mehr klickbar
        call(host, "search_tag_finished", "query", "IAR Betrieb", "ok", "true");
        onEdt(() -> {
            chat.completeAssistantMessage();
            return null;
        });
        refresh(parts);
        assertEquals("IAR Betrieb (schon gesucht)", onEdt(() -> parts.strip().suggestionLabels()).get(0));
        JButton done = onEdt(() -> parts.strip().suggestionButton("IAR Betrieb"));
        assertFalse(onEdt(done::isEnabled));
        assertTrue(onEdt(done::getIcon).getClass().getSimpleName().equals("Check"));

        // Fehler: Kreispfeil, ein Klick wiederholt die Suche
        onEdt(() -> {
            parts.search("IAR Planung");
            return null;
        });
        call(host, "search_tag_finished", "query", "IAR Planung", "ok", "false");
        refresh(parts);
        JButton retry = onEdt(() -> parts.strip().suggestionButton("IAR Planung"));
        assertTrue(onEdt(retry::isEnabled));
        assertEquals("Retry", onEdt(retry::getIcon).getClass().getSimpleName());
        onEdt(() -> {
            retry.doClick();
            return null;
        });
        assertEquals("Wissenssuche: IAR Planung", prompts.get(prompts.size() - 1));
    }

    @Test
    public void aSearchThatNeverFinishedBecomesRetryableWhenTheChatStopsWorking() throws Exception {
        final ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("stalled"),
                FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        host.markGreeted();
        call(host, "offer_searches", "suggestions_json", "[{\"query\":\"IAR Betrieb\",\"purpose\":\"\"}]");
        final List<String> prompts = new ArrayList<String>();
        final ChatShellModel chat = onEdt(() -> new ChatShellModel(System::currentTimeMillis));
        final ResearchWorkflowAssembly.Parts parts = onEdt(() -> ResearchWorkflowAssembly.create(host,
                (visible, prompt) -> prompts.add(prompt), chat, SwingUtilities::invokeLater, (id, title) -> { },
                title -> { }));
        onEdt(() -> {
            parts.search("IAR Betrieb");
            return null;
        });
        onEdt(chat::beginAssistantMessage);
        refresh(parts);
        assertFalse(onEdt(() -> parts.strip().suggestionButton("IAR Betrieb").isEnabled()));

        // die Agentensitzung endet, ohne dass search_tag_finished kam
        onEdt(() -> {
            chat.failAssistantMessage("Die Agentensitzung wurde beendet.");
            return null;
        });
        refresh(parts);
        assertEquals("IAR Betrieb (unterbrochen)", onEdt(() -> parts.strip().suggestionLabels()).get(0));
        JButton retry = onEdt(() -> parts.strip().suggestionButton("IAR Betrieb"));
        assertTrue(onEdt(retry::isEnabled));
        assertEquals("Retry", onEdt(retry::getIcon).getClass().getSimpleName());
        onEdt(() -> {
            retry.doClick();
            return null;
        });
        assertEquals(Arrays.asList("Wissenssuche: IAR Betrieb", "Wissenssuche: IAR Betrieb"), prompts);

        // nach einem gewöhnlich beendeten Turn ohne Rückmeldung ebenso
        onEdt(chat::beginAssistantMessage);
        onEdt(() -> {
            chat.completeAssistantMessage();
            return null;
        });
        refresh(parts);
        assertTrue(onEdt(() -> parts.strip().suggestionButton("IAR Betrieb").isEnabled()));
    }

    private static McpToolResult call(ResearchAgentHost host, String name, String... pairs) {
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
            args.put(pairs[i], pairs[i + 1]);
        }
        assertTrue(name, tools.containsKey(name));
        return tools.get(name).getHandler().invoke(new McpToolCall(name, args));
    }

    private static void refresh(final ResearchWorkflowAssembly.Parts parts) throws Exception {
        Thread.sleep(50); // Zustandshörer laufen auf einem eigenen Thread
        onEdt(() -> {
            parts.refresh();
            return null;
        });
        onEdt(() -> null);
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
