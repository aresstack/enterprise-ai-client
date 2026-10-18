package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatQuote;
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
import com.aresstack.enterpriseai.astu.acropolis.history.ChatResourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.history.ResourceReferenceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.KnowledgeContextRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.QuoteRecord;
import com.aresstack.enterpriseai.astu.acropolis.rag.ConversationKnowledgeContext;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
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
    private ChatConversationId recordedConversation;
    private long createdAt;
    private String renamedTitle; // vom Nutzer gewählter Titel des aktuellen Chats, sonst null
    private boolean restoring;
    /** Der gespeicherte Wissenskontext des geöffneten Chats, bis die Unterhaltung einen eigenen hat. */
    private KnowledgeContextRecord openedKnowledge;

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
            for (ChatMessageRecord message : record.getMessages()) {
                restore(message);
            }
        } finally {
            restoring = false;
        }
        openedKnowledge = record.getKnowledgeContext();
        if (openedKnowledge != null && !openedKnowledge.getChunks().isEmpty()) {
            // Die Auszüge kommen aus dem Index; eine passende Folgefrage muss dann nicht neu suchen.
            List<KnowledgeChunkId> ids = new ArrayList<KnowledgeChunkId>();
            for (KnowledgeContextRecord.Chunk chunk : openedKnowledge.getChunks()) {
                try {
                    ids.add(KnowledgeChunkId.parse(chunk.getChunkId()));
                } catch (IllegalArgumentException invalid) {
                    // unlesbare Kennung: dieser Auszug fällt weg
                }
            }
            // Only under the project scope it was saved with: a chat moved since then gets nothing back (#143).
            chat.restoreKnowledgeContext(target, openedKnowledge.getQueries(), ids, openedKnowledge.getScope());
        }
        chat.setCapabilities(record.isBrowserAllowed(), record.isKnowledgeAllowed());
        chat.setBrowserBudget(record.getBrowserBudget());
        recordedConversation = target;
        createdAt = record.getCreatedAt();
        renamedTitle = record.isTitleEdited() ? record.getTitle() : null;
        chatService.closeConversation(previous);
        return true;
    }

    /** Saves the current chat after its composer switches changed (#141); a chat without messages is not saved. */
    public void capabilitiesChanged() {
        save();
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

    /**
     * Der Wissenskontext der Unterhaltung steht fest (UI-Thread, vor der Antwort): er kommt sofort in den gespeicherten
     * Chat, damit er auch eine gescheiterte Antwort und einen Neustart überlebt.
     */
    void knowledgeContextChanged(ChatConversationId conversation) {
        if (conversation.equals(chat.conversationId())) {
            save();
        }
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
    public void entryRemoved(TranscriptEntry entry) {
        // An answered turn removes its research card: the balance saved meanwhile must leave the record too.
        if (!restoring && entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
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
                    message.getCreatedAt(), names, pages, resourcesOf(message.getReferences()), quotesOf(message));
            if (!message.getAttachments().isEmpty()) {
                storedAttachments.put(entry.getId(), new ArrayList<AttachmentRecord>(message.getAttachments()));
            }
        } else if (message.isAssistant()) {
            TranscriptEntry entry = model.restoreEntry(TranscriptEntry.Author.ASSISTANT, message.getText(),
                    message.getCreatedAt(), Collections.<String>emptyList());
            if (!message.getToolCalls().isEmpty()) {
                toolCalls.put(entry.getId(), new ArrayList<ToolCallRecord>(message.getToolCalls()));
            }
            // Die gespeicherten Quellen gehören wieder an die Antwort: dieselbe aufklappbare Quellenliste wie vor dem
            // Schließen, und beim nächsten Speichern kommen sie aus der Zeile selbst (kein Schattenzustand).
            List<SourceReference> sources = referencesOf(message.getSources());
            if (!sources.isEmpty()) {
                model.attachSources(entry, sources);
            }
        } else if (message.isInfo() && !message.getText().trim().isEmpty()) {
            model.restoreEntry(TranscriptEntry.Author.NOTICE, message.getText(), message.getCreatedAt(),
                    Collections.<String>emptyList());
        }
    }

    /**
     * Die Ressourcen des aktuellen Chats (UI-Thread): Dateien, Seiten und verwendete Quellen als fachliche Projektion
     * ({@link ChatResourceCatalog}) desselben Datensatzes, den Speichern schreiben würde; je stabiler Kennung einmal.
     */
    public List<ChatResource> resources() {
        ChatRecord record = new ChatRecord(chat.conversationId().value(), 0L);
        for (TranscriptEntry entry : model.getEntries()) {
            ChatMessageRecord message = toRecord(entry);
            if (message != null) {
                record.getMessages().add(message);
            }
        }
        ChatResourceCatalog catalog = ChatResourceCatalog.of(record);
        List<ChatResource> resources = new ArrayList<ChatResource>(catalog.size());
        for (AttachmentRecord attachment : catalog.attachments()) {
            resources.add(new ChatResource(ChatResource.Kind.FILE, attachment.getId(), attachment.getFileName(),
                    size(attachment.getSize())));
        }
        for (BookmarkRecord page : catalog.pages()) {
            resources.add(new ChatResource(ChatResource.Kind.PAGE, page.getUri(), page.getTitle(), page.getUri()));
        }
        for (SourceRecord source : catalog.sources()) {
            resources.add(new ChatResource(ChatResource.Kind.SOURCE, source.getResourceId(), source.getTitle(),
                    source.getLocation()));
        }
        return resources;
    }

    private static String size(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
                : (bytes / (1024 * 1024)) + " MB";
    }

    /** Die ausdrücklichen Verweise einer Nutzernachricht zum Speichern (Art, Kennung, Titel). */
    static List<ResourceReferenceRecord> referenceRecordsOf(TranscriptEntry entry) {
        List<ResourceReferenceRecord> records = new ArrayList<ResourceReferenceRecord>(entry.getReferences().size());
        for (ChatResource reference : entry.getReferences()) {
            String kind = reference.kind() == ChatResource.Kind.FILE ? ResourceReferenceRecord.KIND_ATTACHMENT
                    : reference.kind() == ChatResource.Kind.PAGE ? ResourceReferenceRecord.KIND_PAGE
                    : ResourceReferenceRecord.KIND_SOURCE;
            records.add(new ResourceReferenceRecord(kind, reference.id(), reference.title()));
        }
        return records;
    }

    /** Gespeicherte Verweise als Anzeigewerte; unlesbare (unbekannte Art, ohne Kennung) fallen weg. */
    static List<ChatResource> resourcesOf(List<ResourceReferenceRecord> records) {
        List<ChatResource> resources = new ArrayList<ChatResource>(records.size());
        for (ResourceReferenceRecord record : records) {
            if (!record.isValid()) {
                continue;
            }
            ChatResource.Kind kind = ResourceReferenceRecord.KIND_ATTACHMENT.equals(record.getKind())
                    ? ChatResource.Kind.FILE
                    : ResourceReferenceRecord.KIND_PAGE.equals(record.getKind()) ? ChatResource.Kind.PAGE
                    : ChatResource.Kind.SOURCE;
            resources.add(new ChatResource(kind, record.getId(), record.getTitle(), ""));
        }
        return resources;
    }

    /** Schreibt den aktuellen Chat aus den Zeilen des Models. */
    private void save() {
        ChatConversationId conversation = chat.conversationId();
        List<TranscriptEntry> entries = model.getEntries();
        if (!conversation.equals(recordedConversation)) {
            recordedConversation = conversation;
            renamedTitle = null;
            openedKnowledge = null;
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
        record.setKnowledgeContext(knowledgeOf(conversation));
        record.setBrowserAllowed(chat.browserAllowed());
        record.setKnowledgeAllowed(chat.knowledgeAllowed());
        record.setBrowserBudget(chat.browserBudget() == BrowserBudget.DEFAULT ? 0 : chat.browserBudget());
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
                        entry.getCreatedAtMillis(), attachmentsOf(entry)).withBookmarks(bookmarksOf(entry))
                        .withReferences(referenceRecordsOf(entry)).withQuotes(quotesOf(entry));
            case NOTICE:
                return new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, entry.getText(),
                        entry.getCreatedAtMillis(), null);
            case ACTIVITY:
                // Werkzeugwolken gehören zum Lauf, nicht zum gespeicherten Gespräch. Only the balance of a research
                // card that outlived its turn (stopped or failed, #156) stays: it comes back as a notice line.
                if (ChatResearchCard.TITLE.equals(entry.getActivityTitle())
                        && entry.getState() != TranscriptEntry.State.STREAMING
                        && !entry.getActivitySummary().trim().isEmpty()) {
                    return new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, entry.getActivitySummary(),
                            entry.getCreatedAtMillis(), null);
                }
                return null;
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

    /**
     * Der Wissenskontext zum Speichern: nur Suchanfragen und Kennungen der Auszüge (der Text steht im Index); solange
     * ein geöffneter Chat noch keinen eigenen hat, der gespeicherte.
     */
    private KnowledgeContextRecord knowledgeOf(ChatConversationId conversation) {
        ConversationKnowledgeContext context = chat.knowledgeContext(conversation);
        if (context.isEmpty()) {
            // The saved context only survives while the chat keeps the project scope it was saved under (#143).
            return conversation.equals(recordedConversation) && openedKnowledge != null
                    && chat.projectContext(conversation).sameScope(openedKnowledge.getScope()) ? openedKnowledge : null;
        }
        List<KnowledgeContextRecord.Chunk> chunks = new ArrayList<KnowledgeContextRecord.Chunk>();
        for (RetrievedChunk hit : context.hits()) {
            chunks.add(new KnowledgeContextRecord.Chunk(hit.chunk().id().value(), hit.chunk().resourceId().value(),
                    hit.chunk().sourceId().value()));
        }
        return new KnowledgeContextRecord(context.queries(), chunks, chat.knowledgeScope(conversation));
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

    /** Die Quellen einer Antwort, wie angezeigt (auch bei einer geöffneten Antwort hängen sie an der Zeile). */
    static List<SourceRecord> sourcesOf(TranscriptEntry entry) {
        if (!entry.hasSources()) {
            return Collections.<SourceRecord>emptyList();
        }
        List<SourceRecord> records = new ArrayList<SourceRecord>(entry.getSources().size());
        for (SourceReference source : entry.getSources()) {
            records.add(new SourceRecord(source.getNumber(), source.getTitle(), source.getHeading(),
                    source.getLocation(), source.getRevision(), source.getScore(), source.getKeywordRank(),
                    source.getSemanticRank(), source.getSourceId(), source.getResourceId(), source.getChunkId()));
        }
        return records;
    }

    /**
     * Die gespeicherten Quellen einer Antwort als Anzeigewerte, in ihrer Reihenfolge. Einträge, aus denen sich keine
     * Quelle bilden lässt (ohne Titel, Ort oder gültige Nummer), werden übergangen; der Rest bleibt sichtbar.
     */
    static List<SourceReference> referencesOf(List<SourceRecord> records) {
        List<SourceReference> references = new ArrayList<SourceReference>(records.size());
        for (SourceRecord record : records) {
            try {
                references.add(new SourceReference(record.getNumber(), record.getTitle(), record.getHeading(),
                        record.getLocation(), record.getRevision(), record.getScore(),
                        Math.max(0, record.getKeywordRank()), Math.max(0, record.getSemanticRank()),
                        record.getSourceId(), record.getResourceId(), record.getChunkId()));
            } catch (IllegalArgumentException unusable) {
                // unvollständiger Eintrag: diese Quelle fällt weg
            }
        }
        return references;
    }

    /** Die Zitate einer Nutzernachricht: Passage, Zeitpunkt der zitierten Antwort und Position in ihrem Markdown. */
    private static List<QuoteRecord> quotesOf(TranscriptEntry entry) {
        List<QuoteRecord> records = new ArrayList<QuoteRecord>(entry.getQuotes().size());
        for (ChatQuote quote : entry.getQuotes()) {
            records.add(new QuoteRecord(quote.selectedText(), quote.messageCreatedAtMillis(), quote.sourceStart(),
                    quote.sourceEnd()));
        }
        return records;
    }

    /**
     * Die gespeicherten Zitate einer Nutzernachricht, jeweils der wiederhergestellten Antwort mit demselben Zeitpunkt
     * zugeordnet (die jüngste davor); ohne sie bleibt die Zeile unbekannt ({@code -1}), die Passage bleibt sichtbar.
     */
    private List<ChatQuote> quotesOf(ChatMessageRecord message) {
        List<ChatQuote> quotes = new ArrayList<ChatQuote>();
        for (QuoteRecord record : message.getQuotes()) {
            if (record.getText().trim().isEmpty()) {
                continue;
            }
            quotes.add(new ChatQuote(restoredAnswerId(record.getSourceCreatedAt()), record.getSourceCreatedAt(),
                    record.getText(), record.getSourceStart(), record.getSourceEnd()));
        }
        return quotes;
    }

    private long restoredAnswerId(long createdAt) {
        List<TranscriptEntry> entries = model.getEntries();
        for (int index = entries.size() - 1; index >= 0; index--) {
            TranscriptEntry entry = entries.get(index);
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT && entry.getCreatedAtMillis() == createdAt) {
                return entry.getId();
            }
        }
        return -1L;
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
