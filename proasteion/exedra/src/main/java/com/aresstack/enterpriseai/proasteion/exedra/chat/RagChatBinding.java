package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.application.chat.RagSourceFilter;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTools;
import com.aresstack.enterpriseai.astu.acropolis.attachment.ReadAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.attachment.SearchAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagOptions;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagSource;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPath;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalWarning;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolRegistry;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet die Chat-Shell mit genau einer Konversation des {@link RagChatUseCase} (AP22): wie
 * {@link ChatServiceBinding}, aber der RAG-Schalter der Shell entscheidet je Nachricht, ob vor dem Turn Wissen
 * gesucht wird.
 *
 * <ul>
 *   <li><b>RAG aus</b>: exakt der bisherige Weg ({@code RagOptions.disabled()} ist {@code ChatService.send}),
 *       gestartet auf dem UI-Thread.</li>
 *   <li><b>RAG an</b>: Nutzernachricht und leere Antwortblase erscheinen sofort, die Blase zeigt
 *       "{@value #RETRIEVING_ACTIVITY}"; das Retrieval blockiert und läuft deshalb auf {@code workExecutor}, nie
 *       auf dem Event-Thread. Sobald der Turn steht, kommen seine Quellen an die Antwort und Hinweise (Suche ganz
 *       oder teilweise ausgefallen, keine Treffer) als eigene Hinweiszeile in den Verlauf; Deltas, Abschluss,
 *       Abbruch und Fehler laufen weiter über den {@code ChatTurn} der Antwort.</li>
 *   <li><b>Quellen</b>: gesucht wird nur in den Quellen, die der {@link RagSourceFilter} erlaubt (die Häkchen im
 *       Drawer); ist keine gewählt, läuft die Nachricht ohne Suche, und ein Hinweis sagt das.</li>
 *   <li><b>Stop</b> während des Retrievals merkt sich den Wunsch und bricht den Turn ab, sobald er existiert;
 *       die Nutzerfrage bleibt dabei in der Historie (AP2-Regel), die Antwortblase endet als abgebrochen.</li>
 * </ul>
 *
 * <p>Suche und Abbruchwunsch gehören zur jeweiligen Anfrage ({@code Request}): beendet ein schnelles Backend
 * eine Antwort, bevor ihre Quellen den UI-Thread erreichen, kann die nächste Frage schon suchen; die späten
 * Quellen landen dann noch an der fertigen Antwort, ohne den Zustand der laufenden Anfrage anzufassen.
 *
 * <p>Alle Model-Änderungen laufen über {@code uiExecutor} (in der Anwendung {@code SwingUtilities::invokeLater});
 * {@code sendRequested} und {@code stopRequested} müssen auf dem UI-Thread gerufen werden. In Verlauf, Quellen
 * und Hinweisen stehen nur Titel, Orte (laut Domänenregel ohne Zugangsdaten), Stände und Scores, nie Tokens;
 * eine gescheiterte Antwort nennt ihre technische Ursache wie {@link ChatServiceBinding#describe}.
 *
 * <p><b>Werkzeuge, Anhänge und Seiten</b> ({@link #enableTools}): Hat die Unterhaltung Anhänge oder Seiten (oder
 * steht {@code chat.tools.enabled=true}), läuft die Nachricht über die Werkzeug-Schleife ({@code /chat/completions}
 * mit {@code tools}, ohne Streaming): neue Dateien werden auf dem Arbeits-Thread abgelegt, dem Modell werden nur
 * Kennung und Name der Anhänge genannt, den Inhalt holt es sich über {@code read_attachment}/{@code search_attachment}.
 * Seiten aus dem Lesebereich gehören ab der Nachricht, mit der sie kamen, zum Chat: das Modell kennt nur Titel und
 * Bookmark und liest den Inhalt mit {@code knowledge_read}. Die Blase zeigt währenddessen, welches Werkzeug läuft;
 * die Antwort erscheint am Stück.
 */
public final class RagChatBinding implements ChatShellActions {

    private static final Logger LOG = Logger.getLogger(RagChatBinding.class.getName());

    static final String RETRIEVING_ACTIVITY = "Wissen wird gesucht …";
    static final String SEND_REJECTED = "Die Nachricht konnte nicht gesendet werden.";
    static final String RETRIEVAL_FAILED_NOTICE =
            "Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.";
    static final String NO_SOURCES_NOTICE =
            "Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.";
    static final String KEYWORD_PATH_FAILED_NOTICE =
            "Die Volltextsuche ist ausgefallen; es wurde nur semantisch gesucht.";
    static final String SEMANTIC_PATH_FAILED_NOTICE =
            "Die semantische Suche ist ausgefallen; es wurde nur im Volltext gesucht.";
    static final String STORING_ACTIVITY = "Anhang wird abgelegt …";
    static final String TOOLS_ACTIVITY = "Antwort wird erstellt …";
    static final String READING_ACTIVITY = "Liest Anhang …";
    static final String SEARCHING_ACTIVITY = "Durchsucht Anhang …";
    static final String READING_PAGE_ACTIVITY = "Liest Seite …";
    static final String ATTACHMENTS_UNAVAILABLE_NOTICE = "Dateianhänge sind in dieser Sitzung nicht verfügbar.";
    static final String PAGES_UNAVAILABLE_NOTICE = "Seiten können in dieser Sitzung nicht zum Chat hinzugefügt werden.";

    private static final DateTimeFormatter REVISION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RagChatUseCase rag;
    private ChatConversationId conversationId;
    private final ChatShellModel model;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final ZoneId zone;
    private final RagSourceFilter sources;
    private ChatTurn runningTurn;
    private Request retrieving; // die Anfrage, deren Suche gerade läuft (nur UI-Thread)
    private ToolSupport tools;
    private boolean conversationHasAttachments; // nur UI-Thread
    private final List<ChatBookmark> bookmarks = new ArrayList<ChatBookmark>(); // Seiten des Chats, nur UI-Thread
    private ChatHistoryBinding history; // nur UI-Thread
    private String reasoningEffort; // nur UI-Thread; null = Standard des Modells
    private String chatModel; // nur UI-Thread; im Composer gewählt, null = Modell der Konfiguration

    /**
     * @param uiExecutor   führt Model-Änderungen auf dem UI-Thread aus
     * @param workExecutor führt das blockierende Retrieval aus (eigener Thread, nie der UI-Thread)
     * @param zone         Zeitzone für die Anzeige des Stands einer Quelle
     */
    public RagChatBinding(RagChatUseCase rag, ChatConversationId conversationId, ChatShellModel model,
                          Executor uiExecutor, Executor workExecutor, ZoneId zone) {
        this(rag, conversationId, model, uiExecutor, workExecutor, zone, RagSourceFilter.unrestricted());
    }

    /** @param sources welche Quellen RAG durchsucht (je Nachricht gefragt) */
    public RagChatBinding(RagChatUseCase rag, ChatConversationId conversationId, ChatShellModel model,
                          Executor uiExecutor, Executor workExecutor, ZoneId zone, RagSourceFilter sources) {
        if (rag == null || conversationId == null || model == null || uiExecutor == null || workExecutor == null
                || zone == null || sources == null) {
            throw new IllegalArgumentException(
                    "rag, conversationId, model, uiExecutor, workExecutor and zone must not be null");
        }
        this.rag = rag;
        this.conversationId = conversationId;
        this.model = model;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.zone = zone;
        this.sources = sources;
    }

    /** Die Unterhaltung, in die {@link #sendRequested} gerade schreibt (nur UI-Thread). */
    public ChatConversationId conversationId() {
        return conversationId;
    }

    /**
     * „Neuer Chat“: ab jetzt geht jede Anfrage in diese Unterhaltung; das Transkript leert der Aufrufer über
     * {@link ChatShellModel#clear()}. Nur auf dem UI-Thread und nur, solange keine Antwort läuft.
     *
     * @throws IllegalStateException während eine Antwort oder eine Suche läuft
     */
    public void startConversation(ChatConversationId conversation) {
        startConversation(conversation, false);
    }

    /**
     * Wie {@link #startConversation(ChatConversationId)}, für einen gespeicherten Chat: {@code hasAttachments}
     * sagt, ob seine Unterhaltung schon Anhänge hat (dann läuft sie weiter über die Werkzeuge). Die Anhänge einer
     * verlassenen Unterhaltung bleiben liegen; sie gehören zu ihrem gespeicherten Chat.
     *
     * @throws IllegalStateException während eine Antwort oder eine Suche läuft
     */
    public void startConversation(ChatConversationId conversation, boolean hasAttachments) {
        startConversation(conversation, hasAttachments, Collections.<ChatBookmark>emptyList());
    }

    /**
     * Wie {@link #startConversation(ChatConversationId, boolean)}, mit den Seiten (Bookmarks), die der gespeicherte
     * Chat schon hat.
     *
     * @throws IllegalStateException während eine Antwort oder eine Suche läuft
     */
    public void startConversation(ChatConversationId conversation, boolean hasAttachments,
                                  List<ChatBookmark> pages) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        if (runningTurn != null || retrieving != null || model.isStreaming()) {
            throw new IllegalStateException("cannot start a new conversation while a response is running");
        }
        this.conversationId = conversation;
        this.conversationHasAttachments = hasAttachments;
        this.bookmarks.clear();
        if (pages != null) {
            for (ChatBookmark page : pages) {
                if (page != null && !bookmarks.contains(page)) {
                    bookmarks.add(page);
                }
            }
        }
    }

    /** Die Seiten (Bookmarks) der aktuellen Unterhaltung (nur UI-Thread). */
    public List<ChatBookmark> bookmarks() {
        return Collections.unmodifiableList(new ArrayList<ChatBookmark>(bookmarks));
    }

    /** Die Chat-Historie, die abgelegte Anhänge mit ihren Kennungen erfährt ({@code null}: keine). */
    void setHistory(ChatHistoryBinding history) {
        this.history = history;
    }

    /** Schaltet Tool-Calling und Dateianhänge ein (Composition Root); {@code null} schaltet sie aus. */
    public void enableTools(final ToolSupport support) {
        this.tools = support;
    }

    @Override
    public void reasoningChanged(String effort) {
        this.reasoningEffort = effort;
    }

    /** Das im Composer gewählte Chat-Modell (Kategorie CHAT); gilt ab der nächsten Nachricht. */
    public void modelChanged(String modelId) {
        this.chatModel = modelId == null || modelId.trim().isEmpty() ? null : modelId.trim();
    }

    /** Optionen dieses Turns: gewähltes Modell und Denkaufwand, sonst {@code null} (Standard). */
    private ChatOptions turnOptions() {
        if (reasoningEffort == null && chatModel == null) {
            return null;
        }
        ChatOptions.Builder options = ChatOptions.builder();
        if (reasoningEffort != null) {
            options.reasoningEffort(reasoningEffort);
        }
        if (chatModel != null) {
            options.model(chatModel);
        }
        return options.build();
    }

    @Override
    public boolean supportsAttachments() {
        return tools != null;
    }

    /** Muss auf dem UI-Thread gerufen werden (wie alle Model-Änderungen). */
    @Override
    public void sendRequested(final String text, boolean ragEnabled) {
        sendRequested(text, ragEnabled, Collections.<Path>emptyList());
    }

    /** Muss auf dem UI-Thread gerufen werden; {@code files} werden der Unterhaltung als Anhänge hinzugefügt. */
    @Override
    public void sendRequested(final String text, boolean ragEnabled, List<Path> files) {
        sendRequested(text, ragEnabled, files, Collections.<ChatPage>emptyList());
    }

    /**
     * Muss auf dem UI-Thread gerufen werden; {@code files} werden der Unterhaltung als Anhänge hinzugefügt,
     * {@code pages} als Seiten (nur Titel und Bookmark, nie ihr Text).
     */
    @Override
    public void sendRequested(final String text, boolean ragEnabled, List<Path> files, List<ChatPage> pages) {
        if (!model.canSend(text)) {
            return;
        }
        final List<ChatPage> newPages = new ArrayList<ChatPage>();
        final List<ChatBookmark> newBookmarks = new ArrayList<ChatBookmark>();
        if (pages != null) {
            for (ChatPage page : pages) {
                try {
                    newBookmarks.add(new ChatBookmark(page.title(), BookmarkUri.parse(page.bookmark()).toString()));
                    newPages.add(page);
                } catch (IllegalArgumentException invalid) {
                    LOG.warning("Seite ohne gültiges Bookmark übergangen: " + page);
                }
            }
        }
        if (!newBookmarks.isEmpty() && (tools == null || tools.knowledgeRead() == null)) {
            model.addNotice(PAGES_UNAVAILABLE_NOTICE);
            return;
        }
        final List<Path> newFiles = files == null ? Collections.<Path>emptyList() : new ArrayList<Path>(files);
        if (!newFiles.isEmpty() && tools == null) {
            model.addNotice(ATTACHMENTS_UNAVAILABLE_NOTICE);
            return;
        }
        List<String> names = new ArrayList<String>(newFiles.size());
        for (Path file : newFiles) {
            names.add(file.getFileName().toString());
        }
        final TranscriptEntry question = model.addUserMessage(text, names, newPages);
        for (ChatBookmark bookmark : newBookmarks) {
            if (!bookmarks.contains(bookmark)) {
                bookmarks.add(bookmark);
            }
        }
        final List<ChatBookmark> chatPages = new ArrayList<ChatBookmark>(bookmarks);
        final TranscriptEntry answer = model.beginAssistantMessage();
        final TurnListener listener = new TurnListener();
        final boolean toolPath = tools != null
                && (tools.alwaysOn() || conversationHasAttachments || !newFiles.isEmpty() || !chatPages.isEmpty());
        RagOptions ragOptions = RagOptions.disabled();
        if (ragEnabled) {
            if (!sources.isRestricted()) {
                ragOptions = RagOptions.enabled();
            } else if (!sources.allowedSources().isEmpty()) {
                ragOptions = RagOptions.enabled().restrictedTo(sources.allowedSources());
            }
        }
        if (!ragOptions.isEnabled() && !toolPath) {
            startWithoutRetrieval(text, listener);
            return;
        }
        final RagOptions options = ragOptions;
        final ChatOptions chatOptions = turnOptions();
        final ToolSupport support = toolPath ? tools : null;
        final ChatConversationId conversation = conversationId;
        final Request request = new Request(answer, listener);
        model.setAssistantActivity(!newFiles.isEmpty() ? STORING_ACTIVITY
                : options.isEnabled() ? RETRIEVING_ACTIVITY : TOOLS_ACTIVITY);
        retrieving = request;
        try {
            workExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final RagChatTurn turn;
                    try {
                        String extraContext = null;
                        ChatCompletionPort via = null;
                        if (support != null) {
                            final List<Attachment> stored = new ArrayList<Attachment>(newFiles.size());
                            for (Path file : newFiles) {
                                stored.add(support.store().add(conversation, file));
                            }
                            if (!newFiles.isEmpty()) {
                                // Erst nach erfolgreicher Ablage: ab jetzt läuft die Unterhaltung über die Werkzeuge,
                                // und der gespeicherte Chat bekommt die Kennungen der Anhänge.
                                uiExecutor.execute(() -> {
                                    if (conversation.equals(conversationId)) {
                                        conversationHasAttachments = true;
                                    }
                                    if (history != null) {
                                        history.attachmentsStored(conversation, question, stored);
                                    }
                                });
                            }
                            AttachmentTools attachments = new AttachmentTools(support.store(), support.extractor(),
                                    conversation);
                            extraContext = join(attachments.context(), ChatBookmark.context(chatPages));
                            List<AiTool> available = new ArrayList<AiTool>();
                            available.add(attachments.registry().find(ReadAttachmentTool.NAME));
                            available.add(attachments.registry().find(SearchAttachmentTool.NAME));
                            if (!chatPages.isEmpty() && support.knowledgeRead() != null) {
                                available.add(support.knowledgeRead());
                            }
                            via = new ChatCompletionsToolLoop(support.chat(), new ToolRegistry(available),
                                    support.loopExecutor(), activity(listener),
                                    ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS);
                            if (options.isEnabled()) {
                                showActivity(listener, RETRIEVING_ACTIVITY);
                            } else {
                                showActivity(listener, TOOLS_ACTIVITY);
                            }
                        }
                        turn = rag.send(conversation, text, options, chatOptions, listener, extraContext, via);
                    } catch (final RuntimeException rejected) {
                        // Use Case hat den Turn abgelehnt (z. B. Konversation beschäftigt): sichtbar machen.
                        LOG.log(Level.WARNING, "Nachricht nicht gestartet: " + rejected.getMessage(), rejected);
                        final String reason = rejected instanceof AttachmentException ? rejected.getMessage() : null;
                        uiExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                reject(request, reason);
                            }
                        });
                        return;
                    }
                    uiExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            attach(request, turn);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            // Der Arbeits-Executor nimmt nichts mehr an (z. B. beim Beenden): die Antwort darf nicht offen bleiben.
            reject(request, null);
        }
    }

    /** Zwei System-Anteile hintereinander; {@code null}, wenn beide fehlen. */
    private static String join(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + "\n\n" + second;
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        ChatTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        } else if (retrieving != null) {
            retrieving.cancelRequested = true;
        }
    }

    /** RAG aus: der bisherige Weg, synchron gestartet wie in {@link ChatServiceBinding}. */
    private void startWithoutRetrieval(String text, TurnListener listener) {
        try {
            ChatTurn turn = rag.send(conversationId, text, RagOptions.disabled(), turnOptions(), listener).turn();
            if (!turn.isDone()) {
                runningTurn = turn;
            }
        } catch (RuntimeException rejected) {
            listener.closed = true;
            model.failAssistantMessage(SEND_REJECTED);
        }
    }

    /** Die Anfrage kam nie zu einem Turn (UI-Thread): Suche beenden, Antwort als gescheitert schließen. */
    private void reject(Request request, String reason) {
        if (retrieving == request) {
            retrieving = null;
        }
        request.listener.closed = true;
        model.failAssistantMessage(reason == null ? SEND_REJECTED : SEND_REJECTED + " " + reason);
    }

    /** Zeigt in der laufenden Antwort, welches Werkzeug gerade läuft. */
    private ToolActivityListener activity(final TurnListener listener) {
        return new ToolActivityListener() {
            @Override
            public void onToolCall(String toolName) {
                showActivity(listener, ReadAttachmentTool.NAME.equals(toolName) ? READING_ACTIVITY
                        : SearchAttachmentTool.NAME.equals(toolName) ? SEARCHING_ACTIVITY
                        : ChatBookmark.READ_TOOL.equals(toolName) ? READING_PAGE_ACTIVITY
                        : "Werkzeug " + toolName + " …");
            }
        };
    }

    private void showActivity(TurnListener listener, final String text) {
        listener.onUi(new Runnable() {
            @Override
            public void run() {
                if (model.isStreaming()) {
                    model.setAssistantActivity(text);
                }
            }
        });
    }

    /** Der RAG-Turn steht (UI-Thread): Quellen und Hinweise anbringen, Abbruchwunsch einlösen. */
    private void attach(Request request, RagChatTurn ragTurn) {
        ChatTurn turn = ragTurn.turn();
        if (!request.answer.hasSources()) {
            model.attachSources(request.answer, describe(ragTurn.sources()));
        }
        String notice = ragTurn.retrievalRequested() ? notice(ragTurn) : null;
        if (notice != null) {
            model.addNotice(notice);
        }
        if (retrieving != request) {
            // Die Antwort war schon fertig, bevor ihre Quellen ankamen; inzwischen sucht die nächste Anfrage.
            return;
        }
        retrieving = null;
        if (request.cancelRequested) {
            turn.cancel();
            return;
        }
        if (!turn.isDone() && !request.listener.closed) {
            runningTurn = turn;
        }
    }

    /**
     * Der Hinweis zu einem RAG-Turn oder {@code null}, wenn alles normal lief. Ein ausgefallener Suchpfad wird
     * auch dann genannt, wenn der andere nichts fand: so bleibt eine unvollständige Suche von einer vollständigen
     * ohne Treffer unterscheidbar.
     */
    static String notice(RagChatTurn turn) {
        if (turn.retrievalFailed()) {
            return RETRIEVAL_FAILED_NOTICE;
        }
        Set<RetrievalPath> failed = EnumSet.noneOf(RetrievalPath.class);
        for (RetrievalWarning warning : turn.warnings()) {
            failed.add(warning.path());
        }
        if (failed.contains(RetrievalPath.KEYWORD) && failed.contains(RetrievalPath.SEMANTIC)) {
            return RETRIEVAL_FAILED_NOTICE;
        }
        StringBuilder notice = new StringBuilder();
        if (failed.contains(RetrievalPath.KEYWORD)) {
            notice.append(KEYWORD_PATH_FAILED_NOTICE);
        } else if (failed.contains(RetrievalPath.SEMANTIC)) {
            notice.append(SEMANTIC_PATH_FAILED_NOTICE);
        }
        if (turn.sources().isEmpty()) {
            if (notice.length() > 0) {
                notice.append(' ');
            }
            notice.append(NO_SOURCES_NOTICE);
        }
        return notice.length() == 0 ? null : notice.toString();
    }

    /** Übersetzt die Quellen des Use Case in Anzeigewerte der Shell. */
    List<SourceReference> describe(List<RagSource> sources) {
        List<SourceReference> references = new ArrayList<SourceReference>(sources.size());
        for (RagSource source : sources) {
            references.add(describe(source));
        }
        return references;
    }

    SourceReference describe(RagSource source) {
        RetrievedChunk hit = source.hit();
        KnowledgeResource resource = source.resource();
        String location = resource.location().isPresent()
                ? resource.location().get().toString()
                : resource.id().value();
        return new SourceReference(source.number(), resource.title(), source.chunk().headingLine(), location,
                revision(resource.revision()), hit.fusedScore(), hit.keywordRank().orElse(0),
                hit.semanticRank().orElse(0));
    }

    /** Stand als Text: Version und Zeitpunkt (Ortszeit), getrennt durch Komma; leer, wenn unbekannt. */
    String revision(KnowledgeRevision revision) {
        if (revision == null || !revision.isKnown()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (!revision.version().isEmpty()) {
            text.append("Version ").append(revision.version());
        }
        if (revision.modifiedAt().isPresent()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(REVISION_TIME.format(revision.modifiedAt().get().atZone(zone)));
        }
        return text.toString();
    }

    /** Eine Nachricht mit Suche: ihre Antwortblase, ihr Listener und ein während der Suche geäußerter Stop. */
    private static final class Request {
        final TranscriptEntry answer;
        final TurnListener listener;
        boolean cancelRequested;

        Request(TranscriptEntry answer, TurnListener listener) {
            this.answer = answer;
            this.listener = listener;
        }
    }

    /** Leitet Callbacks eines Turns auf den UI-Thread; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class TurnListener implements ChatTurnListener {

        private volatile boolean closed;

        @Override
        public void onDelta(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    model.appendAssistantDelta(text);
                }
            });
        }

        @Override
        public void onCompleted(ChatResponse response) {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.completeAssistantMessage();
                }
            });
        }

        @Override
        public void onFailed(final ChatCompletionException error) {
            final String message = ChatServiceBinding.describe(error);
            LOG.log(Level.WARNING, "Chat-Anfrage fehlgeschlagen (" + error.kind() + "): " + error.getMessage(), error);
            finish(new Runnable() {
                @Override
                public void run() {
                    model.failAssistantMessage(message);
                }
            });
        }

        @Override
        public void onCancelled() {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.cancelAssistantMessage();
                }
            });
        }

        private void finish(final Runnable terminal) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    closed = true;
                    runningTurn = null;
                    terminal.run();
                }
            });
        }

        void onUi(final Runnable update) {
            uiExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!closed) {
                        update.run();
                    }
                }
            });
        }
    }
}
