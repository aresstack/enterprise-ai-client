package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.proasteion.application.agent.ChatMessageSnapshot;
import com.aresstack.enterpriseai.proasteion.application.agent.ChatSessionHistoryReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Der gespeicherte Verlauf eines Chats als Lese-Port für {@code chat_history} (askai arch
 * {@code ChatSessionHistoryReader} über der Chat-Ablage): die Datei des Chats ist die eine Wahrheit für den Text,
 * auch für Nachrichten von vor dem Start dieses Prozesses. Abgebrochene Antworten bleiben, wie im Modellverlauf
 * nach dem Öffnen, draußen. Aus jedem Thread nutzbar.
 *
 * <p>Die Kennung einer Nachricht ist Rolle und Entstehungszeit ({@link #messageId}); dieselbe Kennung vergibt
 * {@link ResearchPhaseAttribution} beim Zuordnen zur Phase, sie überlebt Speichern und Öffnen unverändert.
 */
public final class SavedChatHistory implements ChatSessionHistoryReader {

    private final ChatHistoryStore store;

    public SavedChatHistory(ChatHistoryStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    /** Die stabile Kennung einer gespeicherten Nachricht. */
    static String messageId(String role, long createdAtMillis) {
        return role + "@" + createdAtMillis;
    }

    @Override
    public List<ChatMessageSnapshot> readMessages(String sessionId) {
        ChatRecord record = sessionId == null ? null : store.load(sessionId);
        if (record == null) {
            return Collections.emptyList();
        }
        List<ChatMessageSnapshot> messages = new ArrayList<ChatMessageSnapshot>();
        for (ChatMessageRecord message : record.getMessages()) {
            if (message.isCancelled() || message.getText().trim().isEmpty()) {
                continue;
            }
            messages.add(new ChatMessageSnapshot(messageId(message.getRole(), message.getCreatedAt()),
                    message.getRole(), message.getText(), message.getCreatedAt(), ""));
        }
        return messages;
    }
}
