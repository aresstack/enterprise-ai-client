package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Ein Research-Projekt je Chat: zwei Chats mit eigenen Konzeptkarten und Quellen, Wechsel A → B → A, Löschen und
 * Neustart. Kein Kontext eines Chats taucht im anderen auf; einen globalen „current-project“-Zeiger gibt es nicht.
 */
public class ResearchProjectPerChatTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void eachChatHasItsOwnProjectAcrossSwitches() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);

        host.openChat("chat-a");
        fill(host, "Karte A", "page-a", "Quelle A");
        assertEquals("research", host.phaseId());

        // Chat B: leeres Projekt, nichts von A
        host.openChat("chat-b");
        assertEquals("chat-b", host.chatId());
        assertEquals("scoping", host.phaseId());
        assertEquals("new", host.stateId());
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
        fill(host, "Karte B", "page-b", "Quelle B");

        // zurück zu A: Phase, Konzept und Quellen von A, nichts von B
        host.openChat("chat-a");
        assertEquals("research", host.phaseId());
        assertEquals(Collections.singletonList("Quelle A"), titles(host));
        assertTrue(concept(host).contains("Karte A"));
        assertFalse(concept(host).contains("Karte B"));

        // Neustart mit Chat B: B, nicht der zuletzt benutzte Chat A
        ResearchAgentHost restarted = host(root);
        restarted.openChat("chat-b");
        assertEquals(Collections.singletonList("Quelle B"), titles(restarted));
        assertTrue(concept(restarted).contains("Karte B"));
        assertFalse(new File(root, "current-project").exists());
    }

    @Test
    public void deletingTheOpenChatLeavesNoContextBehind() throws Exception {
        File root = folder.newFolder("research");
        ResearchAgentHost host = host(root);
        host.openChat("chat-a");
        fill(host, "Karte A", "page-a", "Quelle A");

        host.deleteChatProject("chat-a");
        assertNull(host.chatId());
        assertFalse(new File(root, "chat-a").exists());
        assertTrue("ohne Projekt keine Quellen", host.sourceRefs().isEmpty());
        assertNull(host.existingConceptService());

        // derselbe Chat-Name später: frisch, ohne Reste
        host.openChat("chat-a");
        assertEquals("scoping", host.phaseId());
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
    }

    @Test
    public void readingAnUnknownChatCreatesNothingAndUnsavedProjectsArePruned() throws Exception {
        File root = folder.newFolder("research");
        // Reste der früheren Ablage und ein nie gespeicherter Chat
        assertTrue(new File(root, "research-1700000000000").mkdirs());
        Files.write(new File(root, "current-project").toPath(), "research-1700000000000".getBytes("UTF-8"));
        assertTrue(new File(root, "chat-unsaved").mkdirs());
        assertTrue(new File(root, "chat-saved").mkdirs());

        ResearchAgentHost host = host(root);
        host.openChat("chat-new");
        assertTrue(host.sourceRefs().isEmpty());
        assertEquals("", host.outline());
        assertEquals("scoping", host.phaseId());
        assertEquals("new", host.stateId());
        assertFalse(host.isWritable("scoping"));
        assertFalse(host.isGreeted());
        assertNull(host.document());
        assertTrue(host.renderedFence().isEmpty() || !new File(root, "chat-new").exists());
        assertFalse("Lesen und Anzeigen legen kein Projekt an", new File(root, "chat-new").exists());

        host.retainProjectsOf(Arrays.asList("chat-saved"));
        assertTrue(new File(root, "chat-saved").isDirectory());
        assertFalse(new File(root, "chat-unsaved").exists());
        assertFalse(new File(root, "research-1700000000000").exists());
        assertFalse(new File(root, "current-project").exists());
    }

    @Test
    public void chatIdsBecomeSafeFolderNames() {
        assertEquals("a_b_c", ResearchProjects.folderName("a/b\\c"));
        assertEquals("_x", ResearchProjects.folderName(".x"));
        assertEquals("_.", ResearchProjects.folderName(".."));
    }

    private static ResearchAgentHost host(File root) {
        return new ResearchAgentHost(root, FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
    }

    /** Eine Konzeptkarte, Start der Recherche, eine übernommene Quelle. */
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

    private static java.util.List<String> titles(ResearchAgentHost host) {
        java.util.List<String> titles = new java.util.ArrayList<String>();
        for (ResearchSourceRef source : host.sourceRefs()) {
            titles.add(source.title());
        }
        return titles;
    }
}
