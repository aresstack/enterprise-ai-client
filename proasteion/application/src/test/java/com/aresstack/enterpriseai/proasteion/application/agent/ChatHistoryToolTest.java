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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * askai arch {@code chat_history} im Werkzeugsatz des Recherche-Agenten: liest den GESPEICHERTEN Verlauf genau des
 * Chats, an den das Research-Projekt gebunden ist, gegliedert nach dem Phasen-Journal des Projekts.
 */
public class ChatHistoryToolTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, List<ChatMessageSnapshot>> saved = new HashMap<String, List<ChatMessageSnapshot>>();
    private final List<String> asked = new ArrayList<String>();
    private final ChatSessionHistoryReader reader = new ChatSessionHistoryReader() {
        @Override
        public List<ChatMessageSnapshot> readMessages(String sessionId) {
            asked.add(sessionId);
            List<ChatMessageSnapshot> messages = saved.get(sessionId);
            return messages == null ? Collections.<ChatMessageSnapshot>emptyList() : messages;
        }
    };

    @Test
    public void readsThePersistedHistoryOfTheBoundChatOnly() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        host.setChatHistoryReader(reader);
        saved.put("chat-a", Arrays.asList(message("u-1", "user", "Was ist IAR?"),
                message("a-1", "assistant", "IAR bündelt Anträge.")));
        saved.put("chat-b", Collections.singletonList(message("u-9", "user", "Etwas ganz anderes")));

        host.openChat("chat-a");
        host.markGreeted(); // legt das Projekt an
        host.attributeMessage("u-1");
        host.attributeMessage("a-1");
        String history = call(host, ResearchControlTools.CHAT_HISTORY);
        assertTrue(history, history.contains("== phase scoping (current)"));
        assertTrue(history, history.contains("[user] Was ist IAR?"));
        assertTrue(history, history.contains("[assistant] IAR bündelt Anträge."));
        assertFalse(history, history.contains("Etwas ganz anderes"));

        host.openChat("chat-b");
        String other = call(host, ResearchControlTools.CHAT_HISTORY, "raw", "true");
        assertTrue(other, other.contains("Etwas ganz anderes"));
        assertFalse(other, other.contains("IAR"));
        assertEquals(Arrays.asList("chat-a", "chat-b"), asked);

        // Neustart: die Phasenzuordnung liegt im Projekt von A
        ResearchAgentHost restarted = host(root);
        restarted.setChatHistoryReader(reader);
        restarted.openChat("chat-a");
        String again = call(restarted, ResearchControlTools.CHAT_HISTORY);
        assertTrue(again, again.contains("== phase scoping (current)"));
        assertFalse(again, again.contains("unknown"));
    }

    @Test
    public void attributionBeforeTheProjectExistsIsKeptWhenItIsCreated() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        host.setChatHistoryReader(reader);
        saved.put("chat-a", Collections.singletonList(message("u-1", "user", "Hallo")));
        host.openChat("chat-a");
        host.attributeMessage("u-1");
        assertFalse("Zuordnen legt kein Projekt an", new File(root, "chat-a").exists());
        host.markGreeted();
        assertTrue(new File(new File(root, "chat-a"), "phase-journal.json").isFile());
    }

    @Test
    public void unattributedMessagesStayUnknownAndRawShowsThem() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("research"));
        host.setChatHistoryReader(reader);
        saved.put("chat-a", Collections.singletonList(message("u-1", "user", "aus dem Chat-Modus")));
        host.openChat("chat-a");
        String summary = call(host, ResearchControlTools.CHAT_HISTORY);
        assertTrue(summary, summary.contains("== phase unknown"));
        assertTrue(summary, summary.contains("1 messages (raw=true for details)"));
        String raw = call(host, ResearchControlTools.CHAT_HISTORY, "raw", "true");
        assertTrue(raw, raw.contains("[user] aus dem Chat-Modus"));
    }

    @Test
    public void eachFinishedPhaseRecordsItsOutcome() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        host.setChatHistoryReader(reader);
        saved.put("chat-a", Arrays.asList(message("u-1", "user", "Recherchiere IAR"),
                message("a-1", "assistant", "Ich suche Quellen."),
                message("u-2", "user", "Schreib das Dokument")));
        host.openChat("chat-a");
        host.markGreeted();
        assertEquals(null, host.dispatch(com.aresstack.enterpriseai.research.domain.state.ResearchCommandType.SUBMIT_SCOPE));
        assertEquals(null, host.dispatch(
                com.aresstack.enterpriseai.research.domain.state.ResearchCommandType.START_RESEARCH));
        host.attributeMessage("u-1");
        host.attributeMessage("a-1");
        host.acceptSource("page-1", "IAR Übersicht", "");

        // Ende des Quellenlaufs: die Erzählung an den Nutzer ist die Zusammenfassung der Phase
        String narrative = host.reportRunFinished("TARGET_REACHED", 3, 1);
        assertEquals(narrative, journal(root).outcomeOf("research"));

        // Freigabe der Gliederung
        assertEquals(null, host.dispatch(
                com.aresstack.enterpriseai.research.domain.state.ResearchCommandType.APPROVE_EVIDENCE));
        assertEquals(null, host.proposeOutline("# IAR\n\n## Was ist IAR\n- Zweck\n\n## Offene Punkte\n"));
        assertEquals(null, host.dispatch(
                com.aresstack.enterpriseai.research.domain.state.ResearchCommandType.APPROVE_OUTLINE));
        assertEquals("Gliederung freigegeben (2 Kapitel): Was ist IAR; Offene Punkte.",
                journal(root).outcomeOf("outline"));

        // document_save
        if ("waiting".equals(host.stateId())) {
            assertEquals(null, host.dispatch(
                    com.aresstack.enterpriseai.research.domain.state.ResearchCommandType.START_DRAFTING));
        }
        host.attributeMessage("u-2");
        assertEquals(null, host.saveDocument("IAR", Collections.singletonList(
                new ResearchDocument.Section("Was ist IAR", "IAR bündelt Anträge.")),
                Collections.<String, String>emptyMap()));
        assertEquals("Dokument „IAR“ gespeichert: 1 Kapitel, 0 Quellen, 0 belegte Aussagen.",
                journal(root).outcomeOf("draft"));

        // chat_history: die abgeschlossene Phase Quellen erscheint als ihre Zusammenfassung
        String history = call(host, ResearchControlTools.CHAT_HISTORY);
        assertTrue(history, history.contains("== phase research\n  summary: " + narrative.split("\n")[0]));
        assertTrue(history, history.contains("== phase draft (current)\n  outcome: Dokument „IAR“ gespeichert"));
    }

    private static ResearchPhaseJournal journal(File root) throws Exception {
        return ResearchPhaseJournal.fromJson(new String(java.nio.file.Files.readAllBytes(
                new File(new File(root, "chat-a"), "phase-journal.json").toPath()), "UTF-8"));
    }

    @Test
    public void withoutReaderTheToolSaysSoHonestly() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("research"));
        host.openChat("chat-a");
        assertEquals("(this host does not expose the persisted chat history to plugins)",
                call(host, ResearchControlTools.CHAT_HISTORY));
    }

    private static ChatMessageSnapshot message(String id, String role, String text) {
        return new ChatMessageSnapshot(id, role, text, 0L, "");
    }

    private static ResearchAgentHost host(File root) {
        return new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
    }

    private static String call(ResearchAgentHost host, String name, String... pairs) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            args.put(pairs[i], pairs[i + 1]);
        }
        for (McpToolContribution tool : ResearchControlTools.toolsFor(host, MODEL)) {
            if (tool.getName().equals(name)) {
                McpToolResult result = tool.getHandler().invoke(new McpToolCall(name, args));
                assertFalse(result.getText(), result.isError());
                return result.getText();
            }
        }
        throw new AssertionError("kein Werkzeug " + name);
    }

    private static final MainModelChat MODEL = new MainModelChat() {
        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            return MainModelChatResult.ok("");
        }

        @Override
        public String modelName() {
            return "fake";
        }
    };
}
