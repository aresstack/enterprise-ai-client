package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;

import java.util.function.LongSupplier;

/**
 * askai arch {@code attributeToCurrentPhase}: jede Nachricht, die im Agent-Modus entsteht (Auftrag, fertige
 * Antwort, Hinweis), wird im Phasen-Journal des Research-Projekts der laufenden Phase zugeordnet, unter derselben
 * Kennung, unter der sie gespeichert wird ({@link SavedChatHistory#messageId}). Nachrichten aus dem Chat-Modus und
 * beim Öffnen wiederhergestellte bleiben ohne Zuordnung („unknown“), nichts wird erraten.
 *
 * <p>Nur auf dem UI-Thread (wie das Model).
 */
public final class ResearchPhaseAttribution implements ChatShellModelListener {

    private final ResearchAgentHost host;
    private final ShellModeModel modes;
    private final LongSupplier clock;
    /** Ab hier entstandene Nachrichten sind neu; ältere kommen aus einem geöffneten Chat. */
    private long clearedAt;

    public ResearchPhaseAttribution(ResearchAgentHost host, ShellModeModel modes, ChatShellModel model,
                                    LongSupplier clock) {
        if (host == null || modes == null || model == null || clock == null) {
            throw new IllegalArgumentException("host, modes, model and clock must not be null");
        }
        this.host = host;
        this.modes = modes;
        this.clock = clock;
        model.addListener(this);
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.USER) {
            attribute(ChatMessageRecord.ROLE_USER, entry);
        } else if (entry.getAuthor() == TranscriptEntry.Author.NOTICE) {
            attribute(ChatMessageRecord.ROLE_INFO, entry);
        }
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT
                && entry.getState() == TranscriptEntry.State.COMPLETE) {
            attribute(ChatMessageRecord.ROLE_ASSISTANT, entry);
        }
    }

    @Override
    public void stateChanged() {
        // zugeordnet wird je Nachricht
    }

    @Override
    public void entriesCleared() {
        clearedAt = clock.getAsLong();
    }

    private void attribute(String role, TranscriptEntry entry) {
        if (modes.getMode() != ShellMode.AGENT || entry.getCreatedAtMillis() < clearedAt) {
            return;
        }
        host.attributeMessage(SavedChatHistory.messageId(role, entry.getCreatedAtMillis()));
    }
}
