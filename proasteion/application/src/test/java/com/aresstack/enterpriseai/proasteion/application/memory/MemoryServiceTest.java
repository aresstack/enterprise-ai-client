package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectStore;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjects;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectsRecord;
import com.aresstack.enterpriseai.astu.approval.ApprovalDecision;
import com.aresstack.enterpriseai.astu.approval.ApprovalPolicy;
import com.aresstack.enterpriseai.astu.approval.ApprovalRequest;
import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
    private final ChatProjects projects = new ChatProjects(new ChatProjectStore() {
        private ChatProjectsRecord record = new ChatProjectsRecord();

        @Override
        public ChatProjectsRecord load() {
            return record;
        }

        @Override
        public void save(ChatProjectsRecord saved) {
            record = saved;
        }
    });
    private final List<ApprovalRequest> asked = new ArrayList<ApprovalRequest>();
    private boolean approve;
    private Runnable whileAsking = () -> { };

    private ToolApprovalGate gate(ApprovalRules rules) {
        return new ToolApprovalGate(() -> rules, (request, timeoutMillis) -> {
            asked.add(request);
            whileAsking.run();
            return ApprovalDecision.of(request, approve ? ApprovalDecision.Outcome.APPROVED
                    : ApprovalDecision.Outcome.REJECTED);
        }, 1000L);
    }

    private MemoryService service(ApprovalRules rules) {
        return new MemoryService(store, "alice", projects, gate(rules));
    }

    private MemoryService service() {
        return service(ApprovalRules.none());
    }

    private static String id(MemoryResult result) {
        Matcher matcher = Pattern.compile("M-\\d{4,}").matcher(result.text());
        assertTrue(result.text(), matcher.find());
        return matcher.group();
    }

    @Test
    public void saveWithoutScopeIsChatScoped() {
        MemoryResult result = service().save("chat-a", "Releases nur dienstags", null);
        assertFalse(result.text(), result.isError());
        assertTrue(result.text().contains("scope: chat"));
        assertEquals(MemoryOwner.chat("chat-a"), store.only().owner());
    }

    @Test
    public void chatMemoryIsInvisibleInOtherChats() {
        service().save("chat-a", "Releases nur dienstags", null);
        assertTrue(service().search("chat-a", "dienstags", null, 0).text().contains("dienstags"));
        assertEquals("No memories found.", service().search("chat-b", "dienstags", null, 0).text());
    }

    @Test
    public void globalMemoryIsVisibleInOtherChatsOfTheSameUserOnly() {
        service().save("chat-a", "Antworte auf Deutsch", "global");
        assertEquals(MemoryOwner.global("alice"), store.only().owner());
        assertTrue(service().search("chat-b", "deutsch", null, 0).text().contains("[global"));
        MemoryService bob = new MemoryService(store, "bob", null, null);
        assertEquals("No memories found.", bob.search("chat-c", "deutsch", null, 0).text());
    }

    @Test
    public void projectScopeWithoutProjectFailsWithoutFallback() {
        MemoryResult result = service().save("chat-a", "Projektregel", "project");
        assertTrue(result.isError());
        assertTrue(store.all.isEmpty());
    }

    @Test
    public void movingAChatWithdrawsAccessToTheOldProjectAtOnce() {
        String a = projects.create("A", null);
        String b = projects.create("B", null);
        assertTrue(projects.assign("chat-a", a));
        String id = id(service().save("chat-a", "Codefreeze ab Montag", "project"));
        assertTrue(service().search("chat-a", "codefreeze", null, 0).text().contains(id));

        assertTrue(projects.assign("chat-a", b));
        assertEquals("No memories found.", service().search("chat-a", "codefreeze", null, 0).text());
        approve = true;
        assertTrue(service().delete("chat-a", id).isError());
        assertTrue(asked.isEmpty());
        assertEquals(1, store.all.size());

        assertTrue(projects.assign("chat-a", null));
        assertEquals("No memories found.", service().search("chat-a", "codefreeze", null, 0).text());
    }

    @Test
    public void projectMemoryIsSharedByTheChatsOfThatProjectOnly() {
        String p = projects.create("P", null);
        projects.assign("chat-a", p);
        projects.assign("chat-b", p);
        service().save("chat-a", "Codefreeze ab Montag", "project");
        assertTrue(service().search("chat-b", "codefreeze", null, 0).text().contains("[project"));
        assertEquals("No memories found.", service().search("chat-c", "codefreeze", null, 0).text());
    }

    @Test
    public void scopeFilterNarrowsButNeverWidens() {
        service().save("chat-a", "lokal", null);
        service().save("chat-a", "ueberall", "global");
        service().save("chat-b", "fremd", null);
        String chatOnly = service().search("chat-a", "", "chat", 0).text();
        assertTrue(chatOnly.contains("lokal"));
        assertFalse(chatOnly.contains("ueberall"));
        assertFalse(chatOnly.contains("fremd"));
        assertTrue(service().search("chat-a", "", "galaxy", 0).isError());
    }

    @Test
    public void chatMemoryIsDeletedDirectlyAndOnlyFromItsChat() {
        String id = id(service().save("chat-a", "eins", null));
        MemoryResult foreign = service().delete("chat-b", id);
        assertTrue(foreign.isError());
        assertEquals(1, store.all.size());

        MemoryResult own = service().delete("chat-a", id);
        assertFalse(own.text(), own.isError());
        assertTrue(store.all.isEmpty());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void globalDeletionAsksAndKeepsTheMemoryWhenNotApproved() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        approve = false;
        assertTrue(service().delete("chat-a", id).isError());
        assertEquals(1, asked.size());
        assertEquals("global", asked.get(0).target().scope());
        assertEquals("delete", asked.get(0).target().action());
        assertEquals(id, asked.get(0).target().targetId());
        assertEquals(1, store.all.size());

        approve = true;
        assertFalse(service().delete("chat-a", id).isError());
        assertTrue(store.all.isEmpty());
    }

    @Test
    public void withoutHostApprovalProjectAndGlobalDeletionsAreBlocked() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        MemoryService noHost = new MemoryService(store, "alice", null, null);
        assertTrue(noHost.delete("chat-a", id).isError());
        assertEquals(1, store.all.size());
    }

    @Test
    public void denyPolicyPreventsDeletionWithoutAsking() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        approve = true;
        assertTrue(service(ApprovalRules.parse("memory_delete:delete/global=DENY")).delete("chat-a", id).isError());
        assertTrue(asked.isEmpty());
        assertEquals(1, store.all.size());
    }

    @Test
    public void staleApprovalDoesNotDeleteAChangedMemory() {
        final String id = id(service().save("chat-a", "Bitte duzen", "global"));
        approve = true;
        whileAsking = () -> store.bumpVersion(MemoryId.parse(id));
        MemoryResult result = service().delete("chat-a", id);
        assertTrue(result.text(), result.isError());
        assertEquals(1, store.all.size());
    }

    @Test
    public void settingsCanAllowGlobalDeletionWithoutAsking() {
        String id = id(service().save("chat-a", "Bitte duzen", "global"));
        assertFalse(service(ApprovalRules.parse("memory_delete:delete/global=ALLOW")).delete("chat-a", id).isError());
        assertTrue(asked.isEmpty());
        assertTrue(store.all.isEmpty());
    }

    @Test
    public void defaultsAreRegisteredWithTheGate() {
        ToolApprovalGate gate = gate(ApprovalRules.none());
        new MemoryService(store, "alice", null, gate);
        assertEquals(3, gate.subRules(MemoryService.DELETE_TOOL).size());
        String id = id(service().save("chat-a", "x", "global"));
        assertEquals(ApprovalPolicy.ASK, gate.policy(MemoryService.DELETE_TOOL,
                java.util.Collections.<String, Object>singletonMap("id", id)));
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
            MemoryResult result = service().save("chat-a", secret, null);
            assertTrue(secret, result.isError());
            assertFalse(result.text().contains(secret));
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
