package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MemoryManagementTest {

    private final MemoryServiceTest.InMemoryStore store = new MemoryServiceTest.InMemoryStore();
    private final MemoryManagement management = new MemoryManagement(store, "alice");

    @Test
    public void listsOnlyTheManagedOwner() {
        store.save(MemoryOwner.project("p1"), "Projekt eins");
        store.save(MemoryOwner.project("p2"), "Projekt zwei");
        store.save(MemoryOwner.global("alice"), "Global Alice");
        store.save(MemoryOwner.global("bob"), "Global Bob");
        store.save(MemoryOwner.chat("c1"), "Chat eins");

        List<MemoryManagement.Item> project = management.list(MemoryManagement.Owner.PROJECT, "p1", "", 0);
        assertEquals(1, project.size());
        assertEquals("Projekt eins", project.get(0).content());
        List<MemoryManagement.Item> global = management.list(MemoryManagement.Owner.GLOBAL, null, null, 0);
        assertEquals(1, global.size());
        assertEquals("Global Alice", global.get(0).content());
        assertTrue(management.list(MemoryManagement.Owner.PROJECT, " ", null, 0).isEmpty());
    }

    @Test
    public void deleteIsBoundToOwnerAndVersion() {
        Memory own = store.save(MemoryOwner.project("p1"), "Projekt eins").memory();
        Memory foreign = store.save(MemoryOwner.project("p2"), "Projekt zwei").memory();

        assertEquals(MemoryManagement.Deletion.NOT_OWNED, management.delete(MemoryManagement.Owner.PROJECT, "p1",
                foreign.id().visible(), foreign.version()));
        assertEquals(MemoryManagement.Deletion.CHANGED, management.delete(MemoryManagement.Owner.PROJECT, "p1",
                own.id().visible(), own.version() + 1));
        assertEquals(2, store.all.size());

        assertEquals(MemoryManagement.Deletion.DELETED, management.delete(MemoryManagement.Owner.PROJECT, "p1",
                own.id().visible(), own.version()));
        assertEquals(MemoryManagement.Deletion.CHANGED, management.delete(MemoryManagement.Owner.PROJECT, "p1",
                own.id().visible(), own.version()));
        assertEquals(1, store.all.size());
    }

    @Test
    public void globalDeleteOnlyForTheRunningUser() {
        Memory bob = store.save(MemoryOwner.global("bob"), "Global Bob").memory();
        assertEquals(MemoryManagement.Deletion.NOT_OWNED, management.delete(MemoryManagement.Owner.GLOBAL, null,
                bob.id().visible(), bob.version()));
    }
}
