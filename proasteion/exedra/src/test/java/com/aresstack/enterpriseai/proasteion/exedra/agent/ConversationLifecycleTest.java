package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Abnahmeszenario des Lebenszyklus: gespeicherten Chat A öffnen, danach Chat B → keinerlei Agent- oder
 * Research-Kontext von A in B; Neuer Chat, Wechsel A → B → A, Löschen des offenen Chats. Die Agentensitzung zählt
 * ihre Enden, das Research-Projekt ist der echte Host über einem Temp-Ordner.
 */
public class ConversationLifecycleTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final FakeChats chats = new FakeChats();
    private final CountingAgent agent = new CountingAgent();

    @Test
    public void savedChatsKeepTheirOwnAgentAndResearchContext() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        chats.saved.add("chat-a");
        chats.saved.add("chat-b");
        chats.current = "chat-0";
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, agent, host);
        lifecycle.start();
        assertEquals("chat-0", host.chatId());

        // A öffnen: neue Agentensitzung, Projekt A
        assertTrue(lifecycle.openChat("chat-a"));
        assertEquals(1, agent.ended);
        assertEquals("chat-a", host.chatId());
        fill(host, "Karte A", "page-a", "Quelle A");

        // B öffnen: wieder neue Sitzung, kein Konzept, keine Quelle, keine Phase von A
        assertTrue(lifecycle.openChat("chat-b"));
        assertEquals(2, agent.ended);
        assertEquals("chat-b", host.chatId());
        assertEquals("scoping", host.phaseId());
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
        fill(host, "Karte B", "page-b", "Quelle B");

        // zurück zu A: Stand von A, nichts von B
        assertTrue(lifecycle.openChat("chat-a"));
        assertEquals(3, agent.ended);
        assertEquals("research", host.phaseId());
        assertEquals("Quelle A", host.sourceRefs().get(0).title());
        assertEquals(1, host.sourceRefs().size());
        assertTrue(concept(host).contains("Karte A"));
        assertFalse(concept(host).contains("Karte B"));

        // derselbe Chat noch einmal: nichts endet, nichts wechselt
        assertTrue(lifecycle.openChat("chat-a"));
        assertEquals(3, agent.ended);
    }

    @Test
    public void newChatStartsAFreshSessionAndProject() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        chats.current = "chat-a";
        chats.saved.add("chat-a");
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, agent, host);
        lifecycle.start();
        fill(host, "Karte A", "page-a", "Quelle A");

        assertTrue(lifecycle.newChat());
        assertEquals(1, agent.ended);
        assertEquals(chats.current, host.chatId());
        assertFalse("chat-a".equals(host.chatId()));
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
        assertFalse("ohne Agent-Turn kein Projektordner", new File(root, chats.current).exists());
    }

    @Test
    public void deletingTheOpenChatLeavesItsContextImmediatelyAndItsProjectAtShutdown() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        chats.current = "chat-a";
        chats.saved.add("chat-a");
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, agent, host);
        lifecycle.start();
        fill(host, "Karte A", "page-a", "Quelle A");

        assertTrue(lifecycle.deleteChat("chat-a"));
        assertEquals(1, agent.ended);
        assertFalse("chat-a".equals(chats.current));
        assertEquals(chats.current, host.chatId());
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
        assertEquals(Collections.singletonList("chat-a"), lifecycle.pendingDeletes());

        lifecycle.purgeDeleted();
        assertEquals(Collections.singletonList("chat-a"), chats.deleted);
        assertFalse(new File(root, "chat-a").exists());
    }

    @Test
    public void restoredChatKeepsItsProject() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        chats.current = "chat-a";
        chats.saved.add("chat-a");
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, agent, host);
        lifecycle.start();
        fill(host, "Karte A", "page-a", "Quelle A");
        lifecycle.deleteChat("chat-a");
        lifecycle.restoreChat("chat-a");
        lifecycle.purgeDeleted();
        assertTrue(chats.deleted.isEmpty());
        assertTrue(new File(root, "chat-a").isDirectory());
        assertTrue(lifecycle.openChat("chat-a"));
        assertEquals("Quelle A", host.sourceRefs().get(0).title());
    }

    @Test
    public void nothingSwitchesWhileTheAgentWorks() throws Exception {
        ResearchAgentHost host = host(folder.newFolder("research"));
        chats.current = "chat-a";
        chats.saved.add("chat-b");
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, agent, host);
        lifecycle.start();
        agent.busy = true;
        assertFalse(lifecycle.openChat("chat-b"));
        assertFalse(lifecycle.newChat());
        assertFalse(lifecycle.deleteChat("chat-a"));
        assertEquals("chat-a", chats.current);
        assertEquals("chat-a", host.chatId());
        assertEquals(0, agent.ended);
    }

    @Test
    public void startDiscardsProjectsWithoutSavedChat() throws Exception {
        File root = folder.newFolder("research");
        assertTrue(new File(root, "chat-gone").mkdirs());
        assertTrue(new File(root, "chat-kept").mkdirs());
        chats.current = "chat-new";
        chats.saved.add("chat-kept");
        new ConversationLifecycle(chats, agent, host(root)).start();
        assertFalse(new File(root, "chat-gone").exists());
        assertTrue(new File(root, "chat-kept").isDirectory());
    }

    @Test
    public void worksWithoutAgentAndResearch() {
        chats.current = "chat-a";
        chats.saved.add("chat-b");
        ConversationLifecycle lifecycle = new ConversationLifecycle(chats, null, null);
        lifecycle.start();
        assertTrue(lifecycle.openChat("chat-b"));
        assertTrue(lifecycle.newChat());
        assertTrue(lifecycle.deleteChat("chat-b"));
        lifecycle.purgeDeleted();
        assertEquals(Collections.singletonList("chat-b"), chats.deleted);
    }

    private static ResearchAgentHost host(File root) {
        return new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
    }

    private static void fill(ResearchAgentHost host, String card, String page, String title) {
        host.markGreeted();
        assertTrue(host.existingConceptService().addCards(Collections.<String>emptyList(),
                Collections.singletonList(card)).isApplied());
        assertNull(host.dispatch(ResearchCommandType.SUBMIT_SCOPE));
        assertNull(host.dispatch(ResearchCommandType.START_RESEARCH));
        assertTrue(host.acceptSource(page, title, "").startsWith("source_id="));
    }

    private static String concept(ResearchAgentHost host) {
        ConceptBranchService service = host.existingConceptService();
        return service == null ? "" : service.snapshot().getDocumentJson();
    }

    private static final class FakeChats implements ConversationLifecycle.Chats {
        String current;
        final Set<String> saved = new LinkedHashSet<String>();
        final List<String> deleted = new ArrayList<String>();
        private int next;

        @Override
        public String currentChatId() {
            return current;
        }

        @Override
        public List<String> savedChatIds() {
            return new ArrayList<String>(saved);
        }

        @Override
        public boolean startNewConversation() {
            current = "chat-new-" + (++next);
            return true;
        }

        @Override
        public boolean open(String chatId) {
            if (!saved.contains(chatId)) {
                return false;
            }
            current = chatId;
            return true;
        }

        @Override
        public void delete(String chatId) {
            saved.remove(chatId);
            deleted.add(chatId);
        }
    }

    private static final class CountingAgent implements ConversationLifecycle.AgentSessions {
        int ended;
        boolean busy;

        @Override
        public boolean isBusy() {
            return busy;
        }

        @Override
        public void endSession() {
            ended++;
        }
    }
}
