package com.aresstack.enterpriseai.memory.h2;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class H2MemoryStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path directory;
    private H2MemoryStore store;

    @Before
    public void open() throws Exception {
        directory = folder.newFolder("memories").toPath();
        store = new H2MemoryStore(directory, 1000);
    }

    @After
    public void close() {
        store.close();
    }

    @Test
    public void savedMemoryIsFoundAfterRestart() {
        MemorySaved saved = store.save(MemoryOwner.chat("chat-a"), "Releases nur dienstags.");
        assertFalse(saved.existing());
        assertEquals(MemoryScope.CHAT, saved.memory().scope());
        store.close();

        store = new H2MemoryStore(directory, 1000);
        List<Memory> found = store.search(Collections.singletonList(MemoryOwner.chat("chat-a")), "dienstags", 10);
        assertEquals(1, found.size());
        assertEquals(saved.memory().id(), found.get(0).id());
        assertEquals("Releases nur dienstags.", found.get(0).content());
    }

    @Test
    public void searchNeverReturnsOtherOwners() {
        store.save(MemoryOwner.chat("chat-a"), "Farbe blau");
        store.save(MemoryOwner.chat("chat-b"), "Farbe rot");
        store.save(MemoryOwner.global("alice"), "Farbe gruen");
        store.save(MemoryOwner.global("bob"), "Farbe gelb");

        List<Memory> found = store.search(Arrays.asList(MemoryOwner.chat("chat-a"), MemoryOwner.global("alice")),
                "farbe", 10);
        assertEquals(2, found.size());
        for (Memory memory : found) {
            assertTrue(memory.owner().equals(MemoryOwner.chat("chat-a"))
                    || memory.owner().equals(MemoryOwner.global("alice")));
        }
        assertTrue(store.search(Collections.<MemoryOwner>emptyList(), "", 10).isEmpty());
    }

    @Test
    public void searchMatchesAllWordsAndTreatsLikeCharactersLiterally() {
        MemoryOwner owner = MemoryOwner.chat("c");
        store.save(owner, "Server prod-01 nutzt Port 8443");
        store.save(owner, "Server test nutzt Port 8080");
        store.save(owner, "100% Abdeckung");
        assertEquals(1, store.search(Collections.singletonList(owner), "server 8443", 10).size());
        assertEquals(1, store.search(Collections.singletonList(owner), "100%", 10).size());
        assertEquals(0, store.search(Collections.singletonList(owner), "_", 10).size());
        assertEquals(3, store.search(Collections.singletonList(owner), "  ", 10).size());
        assertEquals(2, store.search(Collections.singletonList(owner), null, 2).size());
    }

    @Test
    public void duplicateContentReturnsExistingWithoutWriting() {
        MemoryOwner owner = MemoryOwner.chat("c");
        MemorySaved first = store.save(owner, "Kein Deployment am Freitag");
        MemorySaved second = store.save(owner, "  Kein Deployment am Freitag ");
        assertTrue(second.existing());
        assertEquals(first.memory().id(), second.memory().id());
        assertEquals(1, store.search(Collections.singletonList(owner), "", 10).size());
        // Another owner gets its own memory.
        assertNotEquals(first.memory().id(), store.save(MemoryOwner.chat("d"), "Kein Deployment am Freitag")
                .memory().id());
    }

    @Test
    public void idsAreNeverReusedAfterDeletionAndRestart() {
        MemoryOwner owner = MemoryOwner.chat("c");
        Memory first = store.save(owner, "eins").memory();
        assertTrue(store.delete(first.id(), first.version()));
        store.close();
        store = new H2MemoryStore(directory, 1000);
        Memory second = store.save(owner, "eins").memory();
        assertTrue(second.id().number() > first.id().number());
        assertNull(store.find(first.id()));
    }

    @Test
    public void deleteIsBoundToTheVersion() {
        Memory memory = store.save(MemoryOwner.global("alice"), "Bitte duzen").memory();
        assertFalse(store.delete(memory.id(), memory.version() + 1));
        assertEquals(memory.id(), store.find(memory.id()).id());
        assertTrue(store.delete(memory.id(), memory.version()));
        assertFalse(store.delete(memory.id(), memory.version()));
    }

    @Test
    public void deleteAllRemovesOnlyThatOwner() {
        store.save(MemoryOwner.chat("a"), "x1");
        store.save(MemoryOwner.chat("a"), "x2");
        Memory other = store.save(MemoryOwner.chat("b"), "x3").memory();
        Memory global = store.save(MemoryOwner.global("u"), "x4").memory();
        assertEquals(2, store.deleteAll(MemoryOwner.chat("a")));
        assertEquals(other.id(), store.find(other.id()).id());
        assertEquals(global.id(), store.find(global.id()).id());
    }
}
