package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code chat_history} über der echten Chat-Ablage-Form: Nachrichten aus dem Agent-Modus gehören zur laufenden
 * Phase (dieselbe Kennung beim Zuordnen und beim Lesen), Chat-Modus und beim Öffnen wiederhergestellte Nachrichten
 * bleiben „unknown“, abgebrochene Antworten fehlen wie im Modellverlauf.
 */
public class ResearchPhaseAttributionTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final AtomicLong now = new AtomicLong(1000L);
    private final ChatShellModel model = new ChatShellModel(() -> now.incrementAndGet());
    private final ShellModeModel modes = new ShellModeModel(true);
    private final MemoryStore store = new MemoryStore();

    @Test
    public void agentMessagesCarryTheirPhaseInChatHistory() throws Exception {
        ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"), FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        host.setChatHistoryReader(new SavedChatHistory(store));
        host.openChat("chat-a");
        new ResearchPhaseAttribution(host, modes, model, () -> now.incrementAndGet());

        // ein Chat-Turn: ohne Phase
        model.addUserMessage("Nur geplaudert");
        model.beginAssistantMessage();
        model.appendAssistantDelta("Gern.");
        model.completeAssistantMessage();

        // Agent-Modus: Auftrag und Antwort in der Phase Konzept, abgebrochene Antwort fehlt
        modes.select(ShellMode.AGENT);
        host.markGreeted();
        model.addUserMessage("Recherchiere IAR");
        model.beginAssistantMessage();
        model.appendAssistantDelta("Ich schärfe zuerst das Konzept.");
        model.completeAssistantMessage();
        model.addUserMessage("Noch etwas");
        model.beginAssistantMessage();
        model.appendAssistantDelta("hal");
        model.cancelAssistantMessage();
        saveAs("chat-a");

        String summary = host.describeChatHistory(false);
        assertTrue(summary, summary.contains("== phase unknown\n  summary: 2 messages"));
        assertTrue(summary, summary.contains("== phase scoping (current)"));
        assertTrue(summary, summary.contains("[user] Recherchiere IAR"));
        assertTrue(summary, summary.contains("[assistant] Ich schärfe zuerst das Konzept."));
        assertFalse(summary, summary.contains("hal\n"));

        // Öffnen (Leeren + Wiederherstellen) im Agent-Modus ordnet nichts neu zu
        model.clear();
        model.restoreEntry(TranscriptEntry.Author.USER, "Nur geplaudert", 1001L, Collections.<String>emptyList());
        assertEquals(summary, host.describeChatHistory(false));
    }

    /** Schreibt die Zeilen des Models wie die Chat-Historie (Rolle, Text, Entstehungszeit). */
    private void saveAs(String chatId) {
        ChatRecord record = new ChatRecord(chatId, 0L);
        for (TranscriptEntry entry : model.getEntries()) {
            ChatMessageRecord message;
            if (entry.getAuthor() == TranscriptEntry.Author.USER) {
                message = new ChatMessageRecord(ChatMessageRecord.ROLE_USER, entry.getText(),
                        entry.getCreatedAtMillis(), null);
            } else if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                message = new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, entry.getText(),
                        entry.getCreatedAtMillis(), null);
                if (entry.getState() == TranscriptEntry.State.CANCELLED) {
                    message = message.markCancelled();
                }
            } else {
                continue;
            }
            record.getMessages().add(message);
        }
        store.save(record);
    }

    private static final class MemoryStore implements ChatHistoryStore {
        private final Map<String, ChatRecord> records = new HashMap<String, ChatRecord>();

        @Override
        public void save(ChatRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public ChatRecord load(String chatId) {
            return records.get(chatId);
        }

        @Override
        public List<ChatRecord> list() {
            return new ArrayList<ChatRecord>(records.values());
        }

        @Override
        public void delete(String chatId) {
            records.remove(chatId);
        }
    }
}
