package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.chat.ChatConversation;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ein Verlauf für beide Fähigkeiten (askai arch: der gespeicherte Chat ist die eine Wahrheit, Chat- und
 * Agent-Modus arbeiten auf derselben Unterhaltung): jeder abgeschlossene Agenten-Turn kommt als Nutzernachricht und
 * Antwort in die Unterhaltung des {@link ChatService}. Wer danach im Chat-Modus weiterfragt, fragt mit dem Wissen
 * aus dem Agenten-Turn, genau wie nach dem Öffnen des gespeicherten Chats, dessen Verlauf ebenfalls alle Antworten
 * enthält. Abgebrochene und fehlgeschlagene Turns bleiben draußen (wie beim Öffnen).
 *
 * <p>Nur auf dem UI-Thread (wie das Model).
 */
public final class AgentTurnChatMirror implements ChatShellModelListener {

    private final ChatService chatService;
    private final Supplier<ChatConversationId> conversation;
    private final ShellModeModel modes;
    /** Der Nutzertext des laufenden Agenten-Turns, sonst {@code null}. */
    private String agentQuestion;

    public AgentTurnChatMirror(ChatService chatService, Supplier<ChatConversationId> conversation,
                               ShellModeModel modes, ChatShellModel model) {
        if (chatService == null || conversation == null || modes == null || model == null) {
            throw new IllegalArgumentException("chatService, conversation, modes and model must not be null");
        }
        this.chatService = chatService;
        this.conversation = conversation;
        this.modes = modes;
        model.addListener(this);
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.USER) {
            agentQuestion = modes.getMode() == ShellMode.AGENT ? entry.getText() : null;
        }
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        if (entry.getAuthor() != TranscriptEntry.Author.ASSISTANT || agentQuestion == null
                || entry.getState() == TranscriptEntry.State.STREAMING) {
            return;
        }
        String question = agentQuestion;
        agentQuestion = null;
        if (entry.getState() == TranscriptEntry.State.COMPLETE && !entry.getText().trim().isEmpty()
                && !question.trim().isEmpty()) {
            append(question, entry.getText());
        }
    }

    @Override
    public void stateChanged() {
        // gespiegelt wird je abgeschlossener Antwort
    }

    @Override
    public void entriesCleared() {
        agentQuestion = null;
    }

    private void append(String question, String answer) {
        ChatConversationId id = conversation.get();
        try {
            if (chatService.isBusy(id)) {
                return; // kommt nicht vor: Chat und Agent antworten nie gleichzeitig
            }
            ChatConversation current = chatService.conversation(id);
            List<ChatMessage> history = new ArrayList<ChatMessage>(current.messages());
            history.add(ChatMessage.user(question));
            history.add(ChatMessage.assistant(answer));
            chatService.restoreConversation(id, current.systemPrompt(), history);
        } catch (RuntimeException closedOrBusy) {
            // die Unterhaltung wurde inzwischen gewechselt oder geschlossen; der gespeicherte Chat hat den Turn
        }
    }
}
