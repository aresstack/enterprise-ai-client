package com.aresstack.enterpriseai.astu.acropolis.chat.project;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChatProjectsTest {

    private MemoryStore store;
    private ChatProjects projects;

    @Before
    public void setUp() {
        store = new MemoryStore();
        projects = new ChatProjects(store);
    }

    @Test
    public void existingChatsAreWithoutProjectAndKeepTheLegacyScope() {
        EffectiveProjectContext context = projects.resolve("chat-1");
        assertNull(context.projectId());
        assertEquals(EffectiveProjectContext.NONE_SCOPE, context.scope());
        assertTrue("records saved before projects have no scope", context.sameScope(""));
        assertNull(context.systemContext());
    }

    @Test
    public void treeKeepsOrderNestingAndStableIds() {
        String a = projects.create("Alpha", null);
        String b = projects.create("Beta", null);
        String a1 = projects.create("Alpha eins", a);
        assertEquals(Arrays.asList(a, a1, b), ids(projects.projects()));
        assertEquals(1, projects.depth(a1));

        assertTrue(projects.move(b, null, 0));
        assertEquals(Arrays.asList(b, a, a1), ids(projects.projects()));
        assertTrue(projects.rename(a, "Alpha neu"));
        assertEquals("Alpha neu", projects.project(a).getName());
        assertEquals("ids do not depend on name or position", a1, projects.children(a).get(0).getId());
    }

    @Test
    public void cyclesAndInvalidTargetsAreRefused() {
        String a = projects.create("A", null);
        String a1 = projects.create("A1", a);
        String a11 = projects.create("A11", a1);
        assertFalse(projects.canMove(a, a));
        assertFalse(projects.canMove(a, a11));
        assertFalse(projects.move(a, a1, 0));
        assertFalse(projects.canMove(a, "unknown"));
        assertNull(projects.create("X", "unknown"));
        assertNull(projects.create("  ", null));
        assertTrue(projects.move(a11, null, 99));
        assertEquals(0, projects.depth(a11));
    }

    @Test
    public void everyMoveChangesTheScopeAlsoBackAndForth() {
        String a = projects.create("A", null);
        String b = projects.create("B", null);
        assertTrue(projects.assign("chat", a));
        String inA = projects.resolve("chat").scope();
        assertTrue(projects.assign("chat", b));
        assertEquals(b, projects.projectOf("chat"));
        assertTrue(projects.assign("chat", a));
        assertNotEquals("back in A is a new scope", inA, projects.resolve("chat").scope());
        assertTrue(projects.assign("chat", null));
        assertFalse(projects.resolve("chat").sameScope(EffectiveProjectContext.NONE_SCOPE));
        assertFalse("no change, no new generation", projects.assign("chat", null));
        assertFalse(projects.assign("chat", "unknown"));
    }

    @Test
    public void contextChangesApplyAtOnceWithoutInheritance() {
        String a = projects.create("Vertrieb", null);
        String a1 = projects.create("Nord", a);
        projects.updateInstructions(a, "  Sieze den Kunden.  ");
        projects.assign("chat", a);
        EffectiveProjectContext context = projects.resolve("chat");
        assertEquals("Sieze den Kunden.", context.instructions());
        assertTrue(context.systemContext().contains("Projekt \"Vertrieb\""));

        projects.assign("chat", a1);
        assertNull("sub-projects inherit nothing", projects.resolve("chat").systemContext());
    }

    @Test
    public void deletingAProjectReleasesItsChatsAndDeletesNone() {
        String a = projects.create("A", null);
        String a1 = projects.create("A1", a);
        String b = projects.create("B", null);
        projects.assign("one", a);
        projects.assign("two", a1);
        projects.assign("three", b);
        assertEquals(2, projects.chatsWithin(a).size());

        List<String> released = projects.delete(a);
        assertEquals(2, released.size());
        assertTrue(released.containsAll(Arrays.asList("one", "two")));
        assertNull(projects.projectOf("one"));
        assertNull(projects.projectOf("two"));
        assertEquals(b, projects.projectOf("three"));
        assertEquals(Collections.singletonList(b), ids(projects.projects()));
        assertNull(projects.resolve("two").systemContext());
    }

    @Test
    public void stateSurvivesARestart() {
        String a = projects.create("A", null);
        String a1 = projects.create("A1", a);
        projects.updateInstructions(a1, "Kurz antworten.");
        projects.assign("chat", a1);
        projects.assign("left", a);
        projects.assign("left", null);
        String scope = projects.resolve("chat").scope();
        String leftScope = projects.resolve("left").scope();

        ChatProjects restarted = new ChatProjects(store);
        assertEquals(ids(projects.projects()), ids(restarted.projects()));
        assertEquals(a1, restarted.projectOf("chat"));
        assertEquals(scope, restarted.resolve("chat").scope());
        assertEquals("a chat moved out keeps its generation", leftScope, restarted.resolve("left").scope());
        assertEquals("Kurz antworten.", restarted.resolve("chat").instructions());
    }

    @Test
    public void unreadableTreeIsRepairedOnLoad() {
        ChatProjectsRecord broken = new ChatProjectsRecord(Arrays.asList(
                new ChatProjectRecord("x", "X", "y", 0), new ChatProjectRecord("y", "Y", "x", 0),
                new ChatProjectRecord("z", "Z", "gone", 0)),
                Collections.singletonList(new ChatProjectsRecord.Assignment("chat", "gone", 3)));
        store.saved = broken;
        ChatProjects loaded = new ChatProjects(store);
        assertEquals(3, loaded.projects().size());
        assertEquals(0, loaded.depth("z"));
        assertNull(loaded.projectOf("chat"));
        assertFalse(loaded.resolve("chat").sameScope(EffectiveProjectContext.NONE_SCOPE));
    }

    @Test
    public void listenersHearEveryChange() {
        final int[] changes = {0};
        projects.addListener(() -> changes[0]++);
        String a = projects.create("A", null);
        projects.assign("chat", a);
        projects.delete(a);
        assertEquals(3, changes[0]);
    }

    private static List<String> ids(List<ChatProjectRecord> records) {
        List<String> ids = new java.util.ArrayList<String>();
        for (ChatProjectRecord record : records) {
            ids.add(record.getId());
        }
        return ids;
    }

    private static final class MemoryStore implements ChatProjectStore {
        ChatProjectsRecord saved = new ChatProjectsRecord();

        @Override
        public ChatProjectsRecord load() {
            return saved;
        }

        @Override
        public void save(ChatProjectsRecord record) {
            saved = record;
        }
    }
}
