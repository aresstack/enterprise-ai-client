package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.history.AttachmentRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.BookmarkRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.SourceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

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
 *       Anhang- und Seiten-Chips; die Seiten (Bookmarks) gehören wieder zur Unterhaltung. Die Anhänge liegen unverändert im Ordner des Chats, die Werkzeuge finden sie wieder.</li>
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
    private final Supplier<String> systemPrompt;
    private final LongSupplier clock;
    private final Map<Long, List<AttachmentRecord>> storedAttachments = new HashMap<Long, List<AttachmentRecord>>();
    /** Werkzeugaufrufe je Antwort (Zeilen-Kennung), für die Diagnose; gespeicherte kommen beim Öffnen zurück. */
    /** Name, unter dem die Suchen des Clients (Vorabsuche, Nachsuchen) bei den Werkzeugaufrufen stehen. */
    static final String CLIENT_SEARCH = "client_search";
    private final Map<Long, List<ToolCallRecord>> toolCalls = new HashMap<Long, List<ToolCallRecord>>();
    /** Quellen geöffneter Antworten (die wiederhergestellte Zeile zeigt sie nicht), damit Speichern sie behält. */
    private final Map<Long, List<SourceRecord>> restoredSources = new HashMap<Long, List<SourceRecord>>();
    private ChatConversationId recordedConversation;
    private long createdAt;
    private String renamedTitle; // vom Nutzer gewählter Titel des aktuellen Chats, sonst null
    private boolean restoring;

    public ChatHistoryBinding(ChatHistoryStore store, ChatService chatService, RagChatBinding chat,
                              ChatShellModel model, final String systemPrompt, LongSupplier clock) {
        this(store, chatService, chat, model, new Supplier<String>() {
            @Override
            public String get() {
                return systemPrompt;
            }
        }, clock);
    }

    /** Wie oben; der System-Prompt wird beim Öffnen eines gespeicherten Chats frisch gelesen (ohne Neustart). */
    public ChatHistoryBinding(ChatHistoryStore store, ChatService chatService, RagChatBinding chat,
                              ChatShellModel model, Supplier<String> systemPrompt, LongSupplier clock) {
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
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
            chatService.restoreConversation(target, systemPrompt.get(), history);
        } catch (IllegalStateException busy) {
            return false;
        }
        try {
            chat.startConversation(target, record.attachmentCount() > 0, bookmarksOf(record));
        } catch (IllegalStateException busy) {
            chatService.closeConversation(target);
            return false;
        }
        restoring = true;
        try {
            model.clear();
            storedAttachments.clear();
            toolCalls.clear();
            restoredSources.clear();
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

    /**
     * Ein Werkzeugaufruf einer laufenden Antwort ist fertig (UI-Thread): er kommt für die Diagnose mit der Antwort in
     * den gespeicherten Chat (gespeichert wird mit der fertigen Antwort). Gilt nur für die aktuelle Unterhaltung.
     */
    void toolCallTraced(ChatConversationId conversation, TranscriptEntry answer, String toolName, String arguments,
                        String output, boolean succeeded) {
        if (answer == null || !conversation.equals(chat.conversationId())) {
            return;
        }
        List<ToolCallRecord> calls = toolCalls.get(answer.getId());
        if (calls == null) {
            calls = new ArrayList<ToolCallRecord>();
            toolCalls.put(answer.getId(), calls);
        }
        calls.add(new ToolCallRecord(toolName, arguments, succeeded, output, clock.getAsLong()));
    }

    /**
     * Die Suchen des Clients vor einer Antwort (Vorabsuche und Nachsuchen, UI-Thread): sie kommen für die Diagnose als
     * Werkzeugaufrufe {@value #CLIENT_SEARCH} vor denen des Modells in den gespeicherten Chat; Argument ist die
     * Suchanfrage, Ergebnis die Trefferzahl. Gilt nur für die aktuelle Unterhaltung.
     */
    void searchesTraced(ChatConversationId conversation, TranscriptEntry answer, List<String> queries,
                        List<Integer> hitCounts) {
        if (answer == null || !conversation.equals(chat.conversationId())) {
            return;
        }
        List<ToolCallRecord> searches = new ArrayList<ToolCallRecord>(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            int hits = i < hitCounts.size() ? hitCounts.get(i) : -1;
            searches.add(new ToolCallRecord(CLIENT_SEARCH, "{\"query\":" + jsonString(queries.get(i)) + "}",
                    hits >= 0, hits >= 0 ? hits + " Treffer" : "Suche ausgefallen", clock.getAsLong()));
        }
        List<ToolCallRecord> calls = toolCalls.get(answer.getId());
        if (calls == null) {
            calls = new ArrayList<ToolCallRecord>();
            toolCalls.put(answer.getId(), calls);
        }
        calls.addAll(0, searches);
    }

    private static String jsonString(String text) {
        return new com.google.gson.JsonPrimitive(text).toString();
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
            toolCalls.clear();
            restoredSources.clear();
        }
    }

    private void restore(ChatMessageRecord message) {
        if (message.isUser()) {
            List<String> names = new ArrayList<String>();
            for (AttachmentRecord attachment : message.getAttachments()) {
                names.add(attachment.getFileName());
            }
            List<ChatPage> pages = new ArrayList<ChatPage>();
            for (BookmarkRecord bookmark : message.getBookmarks()) {
                if (!bookmark.getUri().isEmpty()) {
                    pages.add(new ChatPage(bookmark.getTitle(), bookmark.getUri()));
                }
            }
            TranscriptEntry entry = model.restoreEntry(TranscriptEntry.Author.USER, message.getText(),
                    message.getCreatedAt(), names, pages);
            if (!message.getAttachments().isEmpty()) {
                storedAttachments.put(entry.getId(), new ArrayList<AttachmentRecord>(message.getAttachments()));
            }
        } else if (message.isAssistant()) {
            TranscriptEntry entry = model.restoreEntry(TranscriptEntry.Author.ASSISTANT, message.getText(),
                    message.getCreatedAt(), Collections.<String>emptyList());
            if (!message.getToolCalls().isEmpty()) {
                toolCalls.put(entry.getId(), new ArrayList<ToolCallRecord>(message.getToolCalls()));
            }
            if (!message.getSources().isEmpty()) {
                restoredSources.put(entry.getId(), new ArrayList<SourceRecord>(message.getSources()));
            }
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
                        entry.getCreatedAtMillis(), attachmentsOf(entry)).withBookmarks(bookmarksOf(entry));
            case NOTICE:
                return new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, entry.getText(),
                        entry.getCreatedAtMillis(), null);
            case ACTIVITY:
                return null; // Werkzeugwolken gehören zum Lauf, nicht zum gespeicherten Gespräch
            default:
                boolean finished = entry.getState() == TranscriptEntry.State.COMPLETE
                        || entry.getState() == TranscriptEntry.State.CANCELLED;
                if (!finished || entry.getText().trim().isEmpty()) {
                    return null;
                }
                ChatMessageRecord answer = new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, entry.getText(),
                        entry.getCreatedAtMillis(), null).withTrace(sourcesOf(entry), toolCalls.get(entry.getId()));
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

    /** Die Quellen einer Antwort, wie angezeigt; bei einer geöffneten Antwort die gespeicherten. */
    private List<SourceRecord> sourcesOf(TranscriptEntry entry) {
        if (!entry.hasSources()) {
            List<SourceRecord> restored = restoredSources.get(entry.getId());
            return restored != null ? restored : Collections.<SourceRecord>emptyList();
        }
        List<SourceRecord> records = new ArrayList<SourceRecord>(entry.getSources().size());
        for (SourceReference source : entry.getSources()) {
            records.add(new SourceRecord(source.getNumber(), source.getTitle(), source.getHeading(),
                    source.getLocation(), source.getRevision(), source.getScore(), source.getKeywordRank(),
                    source.getSemanticRank()));
        }
        return records;
    }

    /** Die Seiten einer Nutzernachricht: nur Bookmark und Titel. */
    private static List<BookmarkRecord> bookmarksOf(TranscriptEntry entry) {
        List<BookmarkRecord> records = new ArrayList<BookmarkRecord>(entry.getPages().size());
        for (ChatPage page : entry.getPages()) {
            records.add(new BookmarkRecord(page.bookmark(), page.title()));
        }
        return records;
    }

    /** Alle Seiten eines gespeicherten Chats; ungültige Bookmarks werden übergangen. */
    private static List<ChatBookmark> bookmarksOf(ChatRecord record) {
        List<ChatBookmark> bookmarks = new ArrayList<ChatBookmark>();
        for (ChatMessageRecord message : record.getMessages()) {
            for (BookmarkRecord bookmark : message.getBookmarks()) {
                try {
                    bookmarks.add(new ChatBookmark(bookmark.getTitle(), BookmarkUri.parse(bookmark.getUri()).toString()));
                } catch (IllegalArgumentException invalid) {
                    // ohne gültiges Bookmark kann das Modell die Seite nicht lesen
                }
            }
        }
        return bookmarks;
    }

    static String title(String text) {
        String line = text.trim().replaceAll("\\s+", " ");
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH - 1) + "…";
    }
}
