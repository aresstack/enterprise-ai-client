package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.attachment.Attachment;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.history.AttachmentRecord;
import com.aresstack.enterpriseai.application.history.ChatHistoryStore;
import com.aresstack.enterpriseai.application.history.ChatMessageRecord;
import com.aresstack.enterpriseai.application.history.ChatRecord;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Hält den Chat-Verlauf persistent (aus askai-java8 arch: {@code OllamaChatPanel} speichert den Nutzer-Turn samt
 * Anhängen, {@code ChatWorkspacePanel} listet, öffnet und löscht gespeicherte Chats):
 *
 * <ul>
 *   <li><b>Speichern</b>: jede abgeschlossene Zeile des {@link ChatShellModel} (Nutzernachricht, fertige oder
 *       abgebrochene Antwort, Hinweis) schreibt den Chat der aktuellen Unterhaltung neu; laufende und
 *       fehlgeschlagene Antworten werden nicht gespeichert. Die Anhänge einer Nutzernachricht kommen mit ihren
 *       Kennungen nach, sobald die {@link RagChatBinding} sie abgelegt hat ({@link #attachmentsStored}).</li>
 *   <li><b>Öffnen</b>: der gespeicherte Verlauf wird im {@link ChatService} unter derselben Kennung wieder
 *       eröffnet, die Anbindung schreibt ab dann dorthin, das Transkript zeigt die Nachrichten samt
 *       Anhang-Chips. Die Anhänge liegen unverändert im Ordner des Chats, die Werkzeuge finden sie wieder.</li>
 *   <li><b>Löschen</b>: Chat-Datei und Anhang-Ordner ({@link ChatHistoryStore#delete}).</li>
 * </ul>
 *
 * <p>Nur auf dem UI-Thread verwenden (wie das Model).
 */
public final class ChatHistoryBinding implements ChatShellModelListener {

    /** Länge, auf die die erste Nutzernachricht als Titel gekürzt wird. */
    static final int TITLE_LENGTH = 80;

    private final ChatHistoryStore store;
    private final ChatService chatService;
    private final RagChatBinding chat;
    private final ChatShellModel model;
    private final String systemPrompt;
    private final LongSupplier clock;
    private final Map<Long, List<AttachmentRecord>> storedAttachments = new HashMap<Long, List<AttachmentRecord>>();
    private ChatConversationId recordedConversation;
    private long createdAt;
    private String renamedTitle; // vom Nutzer gewählter Titel des aktuellen Chats, sonst null
    private boolean restoring;

    public ChatHistoryBinding(ChatHistoryStore store, ChatService chatService, RagChatBinding chat,
                              ChatShellModel model, String systemPrompt, LongSupplier clock) {
        if (store == null || chatService == null || chat == null || model == null || clock == null) {
            throw new IllegalArgumentException("store, chatService, chat, model and clock must not be null");
        }
        this.store = store;
        this.chatService = chatService;
        this.chat = chat;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.clock = clock;
        model.addListener(this);
        chat.setHistory(this);
    }

    /** Alle gespeicherten Chats, zuletzt geänderte zuerst. */
    public List<ChatRecord> savedChats() {
        return store.list();
    }

    /** Der vom Nutzer gewählte Titel des aktuellen Chats, oder {@code null}. */
    public String currentTitle() {
        return chat.conversationId().equals(recordedConversation) ? renamedTitle : null;
    }

    /** Die Kennung des Chats, in den gerade geschrieben wird. */
    public String currentChatId() {
        return chat.conversationId().value();
    }

    /**
     * Öffnet einen gespeicherten Chat. Abgelehnt ({@code false}), solange eine Antwort läuft oder der Chat nicht
     * lesbar ist; der bisherige Chat bleibt dann, wie er ist.
     */
    public boolean open(String chatId) {
        if (chatId == null || model.isStreaming()) {
            return false;
        }
        ChatConversationId previous = chat.conversationId();
        if (previous.value().equals(chatId)) {
            return true;
        }
        ChatRecord record = store.load(chatId);
        if (record == null) {
            return false;
        }
        ChatConversationId target = new ChatConversationId(chatId);
        List<ChatMessage> history = new ArrayList<ChatMessage>();
        for (ChatMessageRecord message : record.getMessages()) {
            if (message.isUser() && !message.getText().trim().isEmpty()) {
                history.add(ChatMessage.user(message.getText()));
            } else if (message.isAssistant() && !message.isCancelled() && !message.getText().trim().isEmpty()) {
                // Abgebrochene Teilantworten bleiben, wie im laufenden ChatService, aus dem Modellverlauf.
                history.add(ChatMessage.assistant(message.getText()));
            }
        }
        try {
            chatService.restoreConversation(target, systemPrompt, history);
        } catch (IllegalStateException busy) {
            return false;
        }
        try {
            chat.startConversation(target, record.attachmentCount() > 0);
        } catch (IllegalStateException busy) {
            chatService.closeConversation(target);
            return false;
        }
        restoring = true;
        try {
            model.clear();
            storedAttachments.clear();
            for (ChatMessageRecord message : record.getMessages()) {
                restore(message);
            }
        } finally {
            restoring = false;
        }
        recordedConversation = target;
        createdAt = record.getCreatedAt();
        renamedTitle = record.isTitleEdited() ? record.getTitle() : null;
        chatService.closeConversation(previous);
        return true;
    }

    /** Löscht einen gespeicherten Chat samt Anhängen (der aktuelle Chat wechselt vorher der Aufrufer). */
    public void delete(String chatId) {
        store.delete(chatId);
    }

    /**
     * Benennt einen Chat um (askai arch: „Umbenennen“ im Menü der Zeile). Der Titel bleibt danach, auch wenn der
     * Chat weiterläuft. Leere Titel werden ignoriert.
     */
    public void rename(String chatId, String newTitle) {
        if (chatId == null || newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }
        String trimmed = newTitle.trim();
        if (chatId.equals(currentChatId())) {
            renamedTitle = trimmed;
        }
        ChatRecord record = store.load(chatId);
        if (record != null) {
            record.rename(trimmed);
            store.save(record);
        }
    }

    /**
     * Die Anhänge einer Nutzernachricht sind abgelegt (UI-Thread): ihre Kennungen kommen in den gespeicherten
     * Chat. Gilt nur, solange die Unterhaltung noch die aktuelle ist.
     */
    void attachmentsStored(ChatConversationId conversation, TranscriptEntry entry, List<Attachment> attachments) {
        if (!conversation.equals(chat.conversationId()) || attachments.isEmpty()) {
            return;
        }
        List<AttachmentRecord> records = new ArrayList<AttachmentRecord>(attachments.size());
        for (Attachment attachment : attachments) {
            records.add(new AttachmentRecord(attachment.id(), attachment.fileName(), attachment.sizeBytes()));
        }
        storedAttachments.put(entry.getId(), records);
        save();
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        if (!restoring && entry.getState() != TranscriptEntry.State.STREAMING) {
            save();
        }
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        if (!restoring && entry.getState() != TranscriptEntry.State.STREAMING) {
            save();
        }
    }

    @Override
    public void stateChanged() {
        // gespeichert wird je Zeile
    }

    @Override
    public void entriesCleared() {
        if (!restoring) {
            storedAttachments.clear();
        }
    }

    private void restore(ChatMessageRecord message) {
        if (message.isUser()) {
            List<String> names = new ArrayList<String>();
            for (AttachmentRecord attachment : message.getAttachments()) {
                names.add(attachment.getFileName());
            }
            TranscriptEntry entry = model.restoreEntry(TranscriptEntry.Author.USER, message.getText(),
                    message.getCreatedAt(), names);
            if (!message.getAttachments().isEmpty()) {
                storedAttachments.put(entry.getId(), new ArrayList<AttachmentRecord>(message.getAttachments()));
            }
        } else if (message.isAssistant()) {
            model.restoreEntry(TranscriptEntry.Author.ASSISTANT, message.getText(), message.getCreatedAt(),
                    Collections.<String>emptyList());
        } else if (message.isInfo() && !message.getText().trim().isEmpty()) {
            model.restoreEntry(TranscriptEntry.Author.NOTICE, message.getText(), message.getCreatedAt(),
                    Collections.<String>emptyList());
        }
    }

    /** Schreibt den aktuellen Chat aus den Zeilen des Models. */
    private void save() {
        ChatConversationId conversation = chat.conversationId();
        List<TranscriptEntry> entries = model.getEntries();
        if (!conversation.equals(recordedConversation)) {
            recordedConversation = conversation;
            renamedTitle = null;
            createdAt = entries.isEmpty() ? clock.getAsLong() : entries.get(0).getCreatedAtMillis();
        }
        ChatRecord record = new ChatRecord(conversation.value(), createdAt);
        String title = null;
        for (TranscriptEntry entry : entries) {
            ChatMessageRecord message = toRecord(entry);
            if (message == null) {
                continue;
            }
            if (title == null && message.isUser() && !message.getText().trim().isEmpty()) {
                title = title(message.getText());
            }
            record.getMessages().add(message);
        }
        if (title == null) {
            return; // ohne Nutzernachricht gibt es nichts zu speichern
        }
        if (renamedTitle != null) {
            record.rename(renamedTitle);
        } else {
            record.setTitle(title);
        }
        record.setModifiedAt(clock.getAsLong());
        store.save(record);
    }

    private ChatMessageRecord toRecord(TranscriptEntry entry) {
        switch (entry.getAuthor()) {
            case USER:
                return new ChatMessageRecord(ChatMessageRecord.ROLE_USER, entry.getText(),
                        entry.getCreatedAtMillis(), attachmentsOf(entry));
            case NOTICE:
                return new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, entry.getText(),
                        entry.getCreatedAtMillis(), null);
            default:
                boolean finished = entry.getState() == TranscriptEntry.State.COMPLETE
                        || entry.getState() == TranscriptEntry.State.CANCELLED;
                if (!finished || entry.getText().trim().isEmpty()) {
                    return null;
                }
                ChatMessageRecord answer = new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, entry.getText(),
                        entry.getCreatedAtMillis(), null);
                return entry.getState() == TranscriptEntry.State.CANCELLED ? answer.markCancelled() : answer;
        }
    }

    /** Die abgelegten Anhänge einer Nutzernachricht; solange die Ablage läuft, nur ihre Namen. */
    private List<AttachmentRecord> attachmentsOf(TranscriptEntry entry) {
        List<AttachmentRecord> stored = storedAttachments.get(entry.getId());
        if (stored != null) {
            return stored;
        }
        List<AttachmentRecord> names = new ArrayList<AttachmentRecord>(entry.getAttachments().size());
        for (String name : entry.getAttachments()) {
            names.add(new AttachmentRecord(null, name, 0L));
        }
        return names;
    }

    static String title(String text) {
        String line = text.trim().replaceAll("\\s+", " ");
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH - 1) + "…";
    }
}
