package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemoryServiceTest {

    private final InMemoryStore store = new InMemoryStore();
    private final Map<String, String> projectOf = new HashMap<String, String>();
    private final List<MemoryDeletionRequest> asked = new ArrayList<MemoryDeletionRequest>();
    private boolean approve;

    private MemoryService service(Map<MemoryScope, MemoryPolicy> policies) {
        return new MemoryService(store, "alice", projectOf::get, policies, request -> {
            asked.add(request);
            return approve;
        });
    }

    private MemoryService service() {
        return service(null);
    }

    private static String id(McpToolResult result) {
        Matcher matcher = Pattern.compile("M-\\d{4,}").matcher(result.getText());
        assertTrue(result.getText(), matcher.find());
        return matcher.group();
    }

    @Test
    public void saveWithoutScopeIsChatScoped() {
        McpToolResult result = service().save("chat-a", "Releases nur dienstags", null);
        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText().contains("scope: chat"));
        assertEquals(MemoryOwner.chat("chat-a"), store.only().owner());
    }

    @Test
    public void chatMemoryIsInvisibleInOtherChats() {
        service().save("chat-a", "Releases nur dienstags", null);
        assertTrue(service().search("chat-a", "dienstags", null, 0).getText().contains("dienstags"));
        assertEquals("No memories found.", service().search("chat-b", "dienstags", null, 0).getText());
    }

    @Test
    public void globalMemoryIsVisibleInOtherChatsOfTheSameUserOnly() {
        service().save("chat-a", "Antworte auf Deutsch", "global");
        assertEquals(MemoryOwner.global("alice"), store.only().owner());
        assertTrue(service().search("chat-b", "deutsch", null, 0).getText().contains("[global"));
        MemoryService bob = new MemoryService(store, "bob", null, null, null);
        assertEquals("No memories found.", bob.search("chat-c", "deutsch", null, 0).getText());
    }

    @Test
    public void projectScopeWithoutProjectFailsWithoutFallback() {
        McpToolResult result = service().save("chat-a", "Projektregel", "project");
        assertTrue(result.isError());
        assertTrue(store.all.isEmpty());
    }

    @Test
    public void movingAChatWithdrawsAccessToTheOldProjectAtOnce() {
        projectOf.put("chat-a", "P-A");
        String id = id(service().save("chat-a", "Codefreeze ab Montag", "project"));
        assertTrue(service().search("chat-a", "codefreeze", null, 0).getText().contains(id));

        projectOf.put("chat-a", "P-B");
        assertEquals("No memories found.", service().search("chat-a", "codefreeze", null, 0).getText());
        approve = true;
        assertTrue(service().delete("chat-a", id).isError());
        assertTrue(asked.isEmpty());
        assertEquals(1, store.all.size());

        projectOf.remove("chat-a");
        assertEquals("No memories found.", service().search("chat-a", "codefreeze", null, 0).getText());
    }

    @Test
    public void scopeFilterNarrowsButNeverWidens() {
        service().save("chat-a", "lokal", null);
        service().save("chat-a", "ueberall", "global");
        service().save("chat-b", "fremd", null);
        String chatOnly = service().search("chat-a", "", "chat", 0).getText();
        assertTrue(chatOnly.contains("lokal"));
        assertFalse(chatOnly.contains("ueberall"));
        assertFalse(chatOnly.contains("fremd"));
        assertTrue(service().search("chat-a", "", "galaxy", 0).isError());
    }

    @Test
    public void chatMemoryIsDeletedDirectlyAndOnlyFromItsChat() {
        String id = id(service().save("chat-a", "eins", null));
        McpToolResult foreign = service().delete("chat-b", id);
        assertTrue(foreign.isError());
        assertEquals(1, store.all.size());

        McpToolResult own = service().delete("chat-a", id);
        assertFalse(own.getText(), own.isError());
        assertTrue(store.all.isEmpty());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void globalDeletionAsksAndKeepsTheMemoryWhenNotApproved() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        approve = false;
        assertTrue(service().delete("chat-a", id).isError());
        assertEquals(1, asked.size());
        assertEquals(MemoryScope.GLOBAL, asked.get(0).scope());
        assertEquals(id, asked.get(0).id().visible());
        assertEquals(1, store.all.size());

        approve = true;
        assertFalse(service().delete("chat-a", id).isError());
        assertTrue(store.all.isEmpty());
    }

    @Test
    public void withoutHostApprovalProjectAndGlobalDeletionsAreBlocked() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        MemoryService noHost = new MemoryService(store, "alice", null, null, null);
        assertTrue(noHost.delete("chat-a", id).isError());
        assertEquals(1, store.all.size());
    }

    @Test
    public void denyPolicyPreventsDeletionWithoutAsking() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        Map<MemoryScope, MemoryPolicy> policies = new EnumMap<MemoryScope, MemoryPolicy>(MemoryScope.class);
        policies.put(MemoryScope.GLOBAL, MemoryPolicy.DENY);
        approve = true;
        assertTrue(service(policies).delete("chat-a", id).isError());
        assertTrue(asked.isEmpty());
        assertEquals(1, store.all.size());
    }

    @Test
    public void staleApprovalDoesNotDeleteAChangedMemory() {
        final String id = id(service().save("chat-a", "Bitte duzen", "global"));
        MemoryService racing = new MemoryService(store, "alice", null, null, request -> {
            store.bumpVersion(request.id());
            return true;
        });
        McpToolResult result = racing.delete("chat-a", id);
        assertTrue(result.getText(), result.isError());
        assertEquals(1, store.all.size());
    }

    @Test
    public void invalidOrUnknownIdsDeleteNothing() {
        service().save("chat-a", "eins", null);
        assertTrue(service().delete("chat-a", "eins").isError());
        assertTrue(service().delete("chat-a", "M-9999").isError());
        assertEquals(1, store.all.size());
    }

    @Test
    public void secretsAreNeitherStoredNorEchoed() {
        String[] secrets = {"Mein Passwort ist Hunter2!", "api_key: abcdef123456",
                "Token = ghp_abcdefghijklmnopqrstuvwxyz0123", "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def",
                "sk-proj-ABCDEFGHIJKLMNOPQRSTUV"};
        for (String secret : secrets) {
            McpToolResult result = service().save("chat-a", secret, null);
            assertTrue(secret, result.isError());
            assertFalse(result.getText().contains(secret));
        }
        assertTrue(store.all.isEmpty());
        assertFalse(service().save("chat-a", "Das Passwort-Feld ist im Login-Dialog oben", null).isError());
    }

    @Test
    public void deletingAChatRemovesOnlyItsMemories() {
        service().save("chat-a", "eins", null);
        service().save("chat-a", "zwei", "global");
        service().save("chat-b", "drei", null);
        service().deleteChat("chat-a");
        assertEquals(2, store.all.size());
    }

    @Test
    public void toolDescriptionsRestrictSavingToExplicitRequests() {
        List<McpToolContribution> tools = MemoryTools.forChat(null, "x");
        assertEquals(MemoryTools.SAVE, tools.get(0).getName());
        String save = tools.get(0).getDescription().toLowerCase(Locale.ROOT);
        assertTrue(save.contains("only when the user explicitly asks"));
        assertTrue(save.contains("never save on your own"));
        assertTrue(save.contains("current chat only"));
        assertTrue(save.contains("secrets"));
        assertTrue(tools.get(2).getDescription().contains("exactly ONE"));
    }

    /** Store double with the port's contract: version-bound delete, ids never reused, duplicates not rewritten. */
    private static final class InMemoryStore implements MemoryStorePort {
        final Map<MemoryId, Memory> all = new LinkedHashMap<MemoryId, Memory>();
        private final AtomicInteger numbers = new AtomicInteger();

        Memory only() {
            assertEquals(1, all.size());
            return all.values().iterator().next();
        }

        void bumpVersion(MemoryId id) {
            Memory m = all.get(id);
            all.put(id, new Memory(m.id(), m.owner(), m.content(), m.createdAt(), m.version() + 1));
        }

        @Override
        public MemorySaved save(MemoryOwner owner, String content) {
            for (Memory memory : all.values()) {
                if (memory.owner().equals(owner) && memory.content().equals(content.trim())) {
                    return new MemorySaved(memory, true);
                }
            }
            Memory memory = new Memory(MemoryId.of(numbers.incrementAndGet()), owner, content.trim(),
                    Instant.EPOCH, 1);
            all.put(memory.id(), memory);
            return new MemorySaved(memory, false);
        }

        @Override
        public List<Memory> search(Collection<MemoryOwner> owners, String query, int limit) {
            List<Memory> result = new ArrayList<Memory>();
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            for (Memory memory : all.values()) {
                if (owners.contains(memory.owner()) && memory.content().toLowerCase(Locale.ROOT).contains(q)
                        && result.size() < limit) {
                    result.add(memory);
                }
            }
            return result;
        }

        @Override
        public Memory find(MemoryId id) {
            return all.get(id);
        }

        @Override
        public boolean delete(MemoryId id, long expectedVersion) {
            Memory memory = all.get(id);
            if (memory == null || memory.version() != expectedVersion) {
                return false;
            }
            all.remove(id);
            return true;
        }

        @Override
        public int deleteAll(MemoryOwner owner) {
            int removed = 0;
            for (java.util.Iterator<Memory> it = all.values().iterator(); it.hasNext(); ) {
                if (it.next().owner().equals(owner)) {
                    it.remove();
                    removed++;
                }
            }
            return removed;
        }
    }
}
