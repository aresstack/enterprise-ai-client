package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.chat.RagSourceFilter;
import com.aresstack.enterpriseai.proasteion.application.chat.ToolUsePolicy;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatDraft;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatQuote;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.EffectiveProjectContext;
import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTools;
import com.aresstack.enterpriseai.astu.acropolis.attachment.ReadAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.attachment.SearchAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.QuoteContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.TurnContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.QuoteFollowUpSuggestion;
import com.aresstack.enterpriseai.astu.acropolis.chat.QuoteFollowUps;
import com.aresstack.enterpriseai.astu.acropolis.chat.QuotedPassage;
import com.aresstack.enterpriseai.astu.acropolis.rag.KnowledgeAcquisition;
import com.aresstack.enterpriseai.astu.acropolis.rag.KnowledgeQuestion;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolCapabilities;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.astu.acropolis.rag.ConversationKnowledgeContext;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatTurn;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagOptions;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagSource;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPath;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalProgress;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.FixedAnswerPort;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolContextResource;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolArguments;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li><b>RAG an</b>: Nutzernachricht und Recherchekarte ({@link ChatResearchCard}, die gelbe Werkzeugwolke wie
 *       beim Agenten) erscheinen sofort; die Karte zählt Suchen, Schritte und Treffer live mit
 *       ({@link RetrievalProgress}) und platzt, sobald der Turn steht. Die Antwortblase erscheint erst mit dem ersten
 *       Antworttext. Das Retrieval blockiert und läuft deshalb auf {@code workExecutor}, nie auf dem Event-Thread. Sobald der Turn steht, kommen seine Quellen an die Antwort und Hinweise (Suche ganz
 *       oder teilweise ausgefallen, keine Treffer) als eigene Hinweiszeile in den Verlauf; Deltas, Abschluss,
 *       Abbruch und Fehler laufen weiter über den {@code ChatTurn} der Antwort. Jeder ausgefallene Suchpfad steht
 *       mit seiner Ursache im technischen Log ({@code RetrievalObserver} der Composition Root).</li>
 *   <li><b>Hinweis zur Wissensbeschaffung</b>: bewertet wird der ganze Turn ({@link KnowledgeAcquisition}), nicht
 *       nur RAG. Auf dem Werkzeugweg kommt der Hinweis deshalb erst nach der Antwort: hat {@code knowledge_read}
 *       eine Seite geliefert, heißt ein ausgefallener Suchpfad nicht „ohne Kontext“.</li>
 *   <li><b>Quellen</b>: gesucht wird nur in den Quellen, die der {@link RagSourceFilter} erlaubt (die Häkchen im
 *       Drawer); ist keine gewählt, läuft die Nachricht ohne Suche und ohne Hinweis als normaler Chat. Ob und womit
 *       gesucht wird, entscheidet die Vorprüfung des {@link RagChatUseCase} (das Modell, keine Wortliste); reicht
 *       der Kontext nicht, sucht er mit einer genaueren Anfrage nach. Eine Frage nach dem Assistenten selbst
 *       ({@link KnowledgeQuestion}) beantwortet der Client aus den Werkzeugen des Turns ({@link ToolCapabilities}).</li>
 *   <li><b>Herkunft</b>: eine diktierte Nachricht ({@link ChatDraft#origin()}) geht mit diesem Wissen an Vorprüfung
 *       und Modell, damit es Erkennungsfehler aus dem Satz auflöst, statt nach ihnen zu suchen.</li>
 *   <li><b>Werkzeugregeln</b> ({@link ToolUsePolicy}): jeder Turn mit Werkzeugen bekommt die Regeln des Clients
 *       zur Werkzeugwahl, unabhängig vom System-Prompt der Konfiguration.</li>
 *   <li><b>Stop</b> während des Retrievals merkt sich den Wunsch, lässt die restlichen Suchschritte aus und bricht
 *       den Turn ab, sobald er existiert; die Nutzerfrage bleibt dabei in der Historie (AP2-Regel), die Karte endet
 *       als abgebrochen und verschwindet mit dem Turn.</li>
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
 * <p><b>Werkzeuge, Anhänge und Seiten</b> ({@link #enableTools}): Hat die Unterhaltung Anhänge oder Seiten, ist
 * mindestens eine Wissensquelle angehakt (oder steht {@code chat.tools.enabled=true}), läuft die Nachricht über die
 * Werkzeug-Schleife ({@code /chat/completions} mit {@code tools}, gestreamt): neue Dateien werden auf dem
 * Arbeits-Thread abgelegt, dem Modell werden nur Kennung und Name der Anhänge genannt, den Inhalt holt es sich über
 * {@code read_attachment}/{@code search_attachment}. Seiten aus dem Lesebereich gehören ab der Nachricht, mit der sie
 * kamen, zum Chat: das Modell kennt nur Titel und Bookmark und liest den Inhalt mit {@code knowledge_read}. Mit
 * angehakter Quelle oder Seiten bekommt das Modell den Wissens-Werkzeugsatz des Agenten ({@code knowledge_search} über
 * die angehakten Quellen, {@code knowledge_read}), auch ohne Seite: so kann es selbst weitere Seiten suchen und lesen.
 * Jeder Werkzeugaufruf ist eine eigene Werkzeugwolke mit Suchbegriff bzw. Seite aus seinen Argumenten (wie beim
 * Agenten, vergänglich bis zum Ende des Turns); die Antwort erscheint wie ohne Werkzeuge Stück für Stück.
 */
public final class RagChatBinding implements ChatShellActions {

    private static final Logger LOG = Logger.getLogger(RagChatBinding.class.getName());

    private final QuoteFollowUps followUps = new QuoteFollowUps();

    /** Der Wissenskontext des vorigen Turns beantwortet die Frage; {@code %d} = Zahl der Quellen. */
    static final String REUSING_SOURCES_ACTIVITY = "Vorhandene Quellen werden verwendet (%d aus dem vorherigen Turn) …";
    static final String REUSE_INSUFFICIENT_ACTIVITY = "Vorhandene Quellen reichen nicht aus, gezielte Nachrecherche …";
    static final String SEND_REJECTED = "Die Nachricht konnte nicht gesendet werden.";
    static final String RETRIEVAL_FAILED_NOTICE =
            "Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.";
    static final String NO_SOURCES_NOTICE =
            "Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.";
    static final String KEYWORD_PATH_FAILED_NOTICE =
            "Die Volltextsuche ist ausgefallen; es wurde nur semantisch gesucht.";
    static final String SEMANTIC_PATH_FAILED_NOTICE =
            "Die semantische Suche ist ausgefallen; es wurde nur im Volltext gesucht.";
    static final String RETRIEVAL_FAILED_PAGE_READ_NOTICE =
            "Die Wissenssuche ist ausgefallen. Die Seite wurde direkt gelesen.";
    static final String KEYWORD_PATH_FAILED_PAGE_READ_NOTICE =
            "Die Volltextsuche in der Wissensbasis ist ausgefallen. Die Seite wurde direkt gelesen.";
    static final String SEMANTIC_PATH_FAILED_PAGE_READ_NOTICE =
            "Die semantische Suche in der Wissensbasis ist ausgefallen. Die Seite wurde direkt gelesen.";
    static final String PAGE_READ_FAILED_NOTICE =
            "Die Seite konnte nicht gelesen werden. Die Antwort entstand ohne Kontext aus der Wissensbasis.";
    static final String STORING_ACTIVITY = "Legt Anhang ab";
    static final String READING_ACTIVITY = "Liest Anhang";
    static final String SEARCHING_ACTIVITY = "Durchsucht Anhang";
    static final String READING_PAGE_ACTIVITY = "Liest Seite";
    static final String READING_PAGES_ACTIVITY = "Liest Seiten";
    static final String SEARCHING_KNOWLEDGE_ACTIVITY = "Internes Wissen durchsuchen";
    static final String RESEARCHING_KNOWLEDGE_ACTIVITY = "Internes Wissen recherchieren";
    static final String SEARCHING_HELP_ACTIVITY = "Anwendungshilfe prüfen";
    /** The first activity of every planned turn: the model plans the answer. */
    static final String PLANNING_ACTIVITY = "Planen der Antwort …";
    static final String PLANNING_KIND = "think";
    static final String READING_HELP_ACTIVITY = "Liest Programmhilfe";
    static final String TOOL_FAILED = "fehlgeschlagen";
    static final String ATTACHMENTS_UNAVAILABLE_NOTICE =
            "Dateianhänge sind in dieser Sitzung nicht verfügbar; die Nachricht ging ohne sie hinaus.";
    static final String REFERENCES_UNAVAILABLE_NOTICE =
            "Bezüge auf Ressourcen des Chats sind in dieser Sitzung nicht verfügbar; die Nachricht ging ohne sie hinaus.";
    static final String PAGES_UNAVAILABLE_NOTICE =
            "Seiten können in dieser Sitzung nicht zum Chat hinzugefügt werden; die Nachricht ging ohne sie hinaus.";

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
    private int activitySeries; // fortlaufende Kennungen der Wolken (nur UI-Thread)

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

    /** Die Ressourcen der aktuellen Unterhaltung aus dem gespeicherten Verlauf; ohne Verlauf keine (UI-Thread). */
    @Override
    public List<ChatResource> chatResources() {
        return history == null ? Collections.<ChatResource>emptyList() : history.resources();
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
        sendRequested(ChatDraft.of(text, ragEnabled).withAttachments(files).withPages(pages));
    }

    /**
     * Der Weg des Composers (UI-Thread): Text, neue Dateien (werden der Unterhaltung als Anhänge hinzugefügt), Seiten
     * (nur Titel und Bookmark), ausdrückliche Verweise auf Ressourcen des Chats und Zitate früherer Antworten
     * ({@link QuotedPassage#context}, nur für diesen Turn). Ein Verweis kopiert nichts: das
     * Modell erfährt für diesen Turn nur Art, Titel und stabile Kennung (Attachment-ID, Bookmark, Resource-ID) und
     * liest den Inhalt bei Bedarf mit den vorhandenen Werkzeugen; die sichtbare Nachricht bleibt der eingegebene Text.
     */
    @Override
    public void sendRequested(ChatDraft draft) {
        final String text = draft.text();
        if (!model.canSend(text)) {
            return;
        }
        final MessageOrigin origin = draft.origin();
        boolean ragEnabled = draft.ragEnabled();
        List<Path> files = draft.attachments();
        List<ChatPage> pages = draft.pages();
        final List<ChatResource> references = new ArrayList<ChatResource>(draft.references());
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
        // Ohne Werkzeuge gehen Seiten und Dateien nicht mit; die Nachricht selbst darf nicht verloren gehen (der
        // Composer hat Text und Chips schon geleert).
        if (!newBookmarks.isEmpty() && (tools == null || tools.knowledgeTool(ChatBookmark.READ_TOOL) == null)) {
            model.addNotice(PAGES_UNAVAILABLE_NOTICE);
            newBookmarks.clear();
            newPages.clear();
        }
        final List<Path> newFiles = files == null || tools == null ? new ArrayList<Path>() : new ArrayList<Path>(files);
        if (files != null && !files.isEmpty() && tools == null) {
            model.addNotice(ATTACHMENTS_UNAVAILABLE_NOTICE);
        }
        List<String> names = new ArrayList<String>(newFiles.size());
        for (Path file : newFiles) {
            names.add(file.getFileName().toString());
        }
        if (!references.isEmpty() && tools == null) {
            model.addNotice(REFERENCES_UNAVAILABLE_NOTICE);
            references.clear();
        }
        // Zitate stehen an der Nutzerzeile (Chips, gespeichert) und gehen für genau diesen Turn als zusätzlicher
        // System-Anteil an das Modell, auf jedem Weg (normaler Chat, Wissenssuche, Werkzeuge).
        final List<ChatQuote> quoted = new ArrayList<ChatQuote>(draft.quotes());
        final String quoteContext = QuotedPassage.context(passagesOf(quoted));
        final TranscriptEntry question = model.addUserMessage(text, names, newPages, references, quoted);
        for (ChatBookmark bookmark : newBookmarks) {
            if (!bookmarks.contains(bookmark)) {
                bookmarks.add(bookmark);
            }
        }
        final List<ChatBookmark> chatPages = new ArrayList<ChatBookmark>(bookmarks);
        final TranscriptEntry answer = model.beginAssistantMessage();
        final TurnListener listener = new TurnListener();
        RagOptions ragOptions = RagOptions.disabled();
        if (ragEnabled) {
            if (!sources.isRestricted()) {
                ragOptions = RagOptions.enabled();
            } else if (!sources.allowedSources().isEmpty()) {
                ragOptions = RagOptions.enabled().restrictedTo(sources.allowedSources());
            }
        }
        // Wissens-Werkzeuge, sobald es etwas zu suchen (angehakte Quelle) oder zu lesen (Seite) gibt; ein Bookmark
        // ist keine Voraussetzung für die Suche. Keine Quelle angehakt und keine Seite: normaler Chat.
        final boolean knowledgeTools = tools != null && !tools.knowledgeTools().isEmpty()
                && (ragOptions.isEnabled() || !chatPages.isEmpty() || ChatReferences.needsKnowledge(references));
        final boolean toolPath = tools != null && (tools.alwaysOn() || conversationHasAttachments
                || !newFiles.isEmpty() || !chatPages.isEmpty() || knowledgeTools || !references.isEmpty());
        // Eine Frage nach dem Assistenten selbst beantwortet der Client aus dem, was dieser Turn tatsächlich anbietet;
        // das Modell schreibt diese Antwort nicht (kein Selbstbild wie Modellname oder Wissensstand), gesucht wird
        // dafür nicht. Ob jede andere Nachricht mit angehakten Quellen wirklich sucht, entscheidet die Vorprüfung im
        // Use Case.
        final boolean capabilityQuestion = KnowledgeQuestion.isAssistantCapability(text);
        if (capabilityQuestion) {
            ragOptions = RagOptions.disabled();
        }
        // The answer planning step is a model request before the answer: such a turn never starts on the UI thread.
        if (!ragOptions.isEnabled() && !toolPath && !rag.plansAnswers()) {
            startWithoutRetrieval(text, listener, quoteContext, capabilityQuestion
                    ? new FixedAnswerPort(ToolCapabilities.answer(Collections.<ToolDefinition>emptyList(), chatModel))
                    : null, origin);
            return;
        }
        final RagOptions options = ragOptions;
        final ChatOptions chatOptions = turnOptions();
        final ToolSupport support = toolPath ? tools : null;
        final ChatConversationId conversation = conversationId;
        final String answerModel = chatModel;
        final Request request = new Request(answer, listener, toolPath, ++activitySeries);
        listener.request = request;
        // Arbeit des Clients steht in Wolken, nie in der Antwortblase: zuerst das Ablegen neuer Anhänge, dann die
        // Recherchekarte der Wissenssuche.
        final String storingKey = newFiles.isEmpty() ? null : "chat-store-" + request.series;
        if (storingKey != null) {
            model.startActivity(storingKey, STORING_ACTIVITY, "store", joinNames(names));
        }
        if (rag.plansAnswers()) {
            request.planningKey = "chat-planning-" + request.series;
            model.startActivity(request.planningKey, PLANNING_ACTIVITY, PLANNING_KIND, "");
        }
        if (options.isEnabled()) {
            // The card appears with the first research step; a turn that does not search shows none.
            request.card = new ChatResearchCard(model, "chat-research-" + request.series);
        }
        retrieving = request;
        try {
            workExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final RagChatTurn turn;
                    try {
                        TurnContext extraContext = TurnContext.empty()
                                .with(TurnContext.Section.QUOTES, quoteContext);
                        ChatCompletionPort via = capabilityQuestion && support == null
                                ? new FixedAnswerPort(ToolCapabilities.answer(
                                        Collections.<ToolDefinition>emptyList(), answerModel))
                                : null;
                        if (support != null) {
                            final List<Attachment> stored = new ArrayList<Attachment>(newFiles.size());
                            for (Path file : newFiles) {
                                stored.add(support.store().add(conversation, file));
                            }
                            if (!newFiles.isEmpty()) {
                                // Erst nach erfolgreicher Ablage: ab jetzt läuft die Unterhaltung über die Werkzeuge,
                                // und der gespeicherte Chat bekommt die Kennungen der Anhänge.
                                listener.onUi(() -> model.finishActivity(storingKey, true, ""));
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
                            List<AiTool> available = new ArrayList<AiTool>();
                            // Nur aktive Werkzeuge (Reiter „Tools“); abgeschaltete bleiben registriert.
                            for (String name : new String[]{ReadAttachmentTool.NAME, SearchAttachmentTool.NAME}) {
                                if (support.isEnabled(name)) {
                                    available.add(attachments.registry().find(name));
                                }
                            }
                            // Offer the product help on every tool turn; knowledge tools only with something
                            // to search or read.
                            for (AiTool tool : support.knowledgeTools()) {
                                if (knowledgeTools || isHelpTool(tool.name())) {
                                    available.add(tool);
                                }
                            }
                            // Memories (#144) bound to this chat: offered on every tool turn.
                            available.addAll(support.chatTools(conversation.value()));
                            List<String> offered = new ArrayList<String>(available.size());
                            for (AiTool tool : available) {
                                offered.add(tool.name());
                            }
                            // One structured turn context (#126); ChatService composes it with the system prompt.
                            extraContext = extraContext
                                    .with(TurnContext.Section.ATTACHMENTS, attachments.context())
                                    .with(TurnContext.Section.BOOKMARKS, ChatBookmark.context(chatPages))
                                    .with(TurnContext.Section.REFERENCES, ChatReferences.context(references))
                                    .withRule(ToolUsePolicy.context(offered))
                                    .withPlanRouting(ToolUsePolicy.routing(offered));
                            // Wer während der Runde ein Werkzeug abschaltet (Reiter „Tools“), nimmt es ab dem
                            // nächsten Aufruf aus dem Angebot; ein schon angefragter Aufruf wird abgelehnt.
                            via = new ChatCompletionsToolLoop(support.chat(),
                                    new ToolRegistry(available, support::isEnabled), support.loopExecutor(),
                                    activity(listener, request, conversation, support, chatPages),
                                    support.maxRounds());
                            if (capabilityQuestion) {
                                via = new FixedAnswerPort(ToolCapabilities.answer(definitions(available, support),
                                        chatOptions == null ? null : chatOptions.model()));
                            }
                        }
                        turn = rag.sendInContext(conversation, text, options, chatOptions, listener, extraContext, via,
                                progress(request), origin);
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
                            attach(request, turn, conversation);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            // Der Arbeits-Executor nimmt nichts mehr an (z. B. beim Beenden): die Antwort darf nicht offen bleiben.
            reject(request, null);
        }
    }

    /** Die Definitionen der Werkzeuge, die gerade aktiv sind (wie sie die Werkzeug-Schleife anbietet). */
    private static List<ToolDefinition> definitions(List<AiTool> available, ToolSupport support) {
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>(available.size());
        for (AiTool tool : available) {
            if (support.isEnabled(tool.name())) {
                definitions.add(tool.definition());
            }
        }
        return definitions;
    }

    /**
     * Anschlussfragen zu einem Zitat: eine Nebenanfrage ({@link QuoteFollowUps}, ohne Werkzeuge, nicht in der
     * Historie) auf {@code workExecutor} mit der Passage und ihrem Umfeld aus der zitierten Antwort; das Ergebnis geht
     * über {@code uiExecutor} an {@code suggestions}. Fällt sie aus oder nimmt der Executor nichts an, kommt nichts.
     * Muss auf dem UI-Thread gerufen werden.
     */
    @Override
    public void quoteFollowUpsRequested(ChatQuote quote, final Consumer<List<String>> suggestions) {
        if (quote == null || suggestions == null) {
            return;
        }
        String answer = "";
        for (TranscriptEntry entry : model.getEntries()) {
            if (entry.getId() == quote.messageId()) {
                answer = entry.getText();
            }
        }
        final QuoteContext context = QuoteContext.around(answer, quote.selectedText(), quote.sourceStart(),
                quote.sourceEnd(), QuoteContext.DEFAULT_SURROUNDING_CHARS);
        final ChatOptions options = turnOptions();
        try {
            workExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    List<QuoteFollowUpSuggestion> found = followUps.suggest(rag.chatService(), context, options);
                    final List<String> texts = new ArrayList<String>(found.size());
                    for (QuoteFollowUpSuggestion suggestion : found) {
                        texts.add(suggestion.text());
                    }
                    if (!texts.isEmpty()) {
                        uiExecutor.execute(() -> suggestions.accept(texts));
                    }
                }
            });
        } catch (RuntimeException rejected) {
            // Arbeits-Executor nimmt nichts an (z. B. beim Beenden): ohne Vorschläge, das Zitat bleibt benutzbar.
        }
    }

    /** Die zitierten Passagen für das Modell (nur der Text; Zeile und Position bleiben in der Oberfläche). */
    static List<QuotedPassage> passagesOf(List<ChatQuote> quotes) {
        List<QuotedPassage> passages = new ArrayList<QuotedPassage>(quotes.size());
        for (ChatQuote quote : quotes) {
            passages.add(new QuotedPassage(quote.selectedText()));
        }
        return passages;
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        ChatTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        } else if (retrieving != null) {
            retrieving.cancelRequested = true;
            retrieving.cancelled = true;
            if (retrieving.card != null) {
                retrieving.card.cancelling();
            }
        }
    }

    /**
     * RAG aus: der bisherige Weg, synchron gestartet wie in {@link ChatServiceBinding}; {@code quoteContext} (Zitate)
     * nur für diesen Turn, sonst {@code null}.
     */
    private void startWithoutRetrieval(String text, TurnListener listener, String quoteContext,
                                       ChatCompletionPort via, MessageOrigin origin) {
        try {
            ChatTurn turn = rag.sendInContext(conversationId, text, RagOptions.disabled(), turnOptions(), listener,
                    TurnContext.empty().with(TurnContext.Section.QUOTES, quoteContext), via, RetrievalProgress.none(),
                    origin).turn();
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
        endPlanning(request);
        if (retrieving == request) {
            retrieving = null;
        }
        request.listener.closed = true;
        model.failAssistantMessage(reason == null ? SEND_REJECTED : SEND_REJECTED + " " + reason);
    }

    /**
     * Jeder Werkzeugaufruf wird eine Werkzeugwolke (askai arch, wie beim Agenten): Titel nach Werkzeug, darunter
     * Suchbegriff, Seite oder Anhang aus den Argumenten des Aufrufs; mit dem Ergebnis platzt sie. Zählt außerdem für
     * die Anfrage, ob Wissenswerkzeuge Kontext geliefert haben. Läuft auf dem Thread der Werkzeug-Schleife, die ihre
     * Aufrufe nacheinander ausführt.
     */
    private ToolActivityListener activity(final TurnListener listener, final Request request,
                                          final ChatConversationId conversation, final ToolSupport support,
                                          final List<ChatBookmark> pages) {
        return new ToolActivityListener() {
            private final AtomicInteger calls = new AtomicInteger();
            private volatile String runningKey;

            @Override
            public void onToolCall(String toolName) {
                onToolCall(toolName, null);
            }

            @Override
            public void onToolCall(final String toolName, String arguments) {
                if (support.diagnostics() != null) {
                    support.diagnostics().onToolCall(toolName, arguments);
                }
                final String key = "chat-tool-" + request.series + "-" + calls.incrementAndGet();
                runningKey = key;
                final String title = toolTitle(toolName);
                final String kind = toolKind(toolName);
                final String detail = toolDetail(toolName, arguments, support, conversation, pages);
                listener.onUi(() -> model.startActivity(key, title, kind, detail));
            }

            @Override
            public void onToolResult(String toolName, final boolean succeeded) {
                if (support.diagnostics() != null) {
                    support.diagnostics().onToolResult(toolName, succeeded);
                }
                if (KnowledgeAcquisition.isResearch(toolName)) {
                    if (!succeeded) {
                        request.toolReadFails.incrementAndGet(); // reads count in onToolContext
                    }
                } else if (KnowledgeAcquisition.deliversContext(toolName)) {
                    (succeeded ? request.toolReads : request.toolReadFails).incrementAndGet();
                }
                final String key = runningKey;
                runningKey = null;
                if (key != null) {
                    listener.onUi(() -> model.finishActivity(key, succeeded, succeeded ? "" : TOOL_FAILED));
                }
            }

            @Override
            public void onToolContext(String toolName, List<ToolContextResource> resources) {
                if (!KnowledgeAcquisition.deliversContext(toolName)) {
                    return;
                }
                if (KnowledgeAcquisition.isResearch(toolName)) {
                    if (resources.isEmpty()) {
                        return; // researched without evidence: no knowledge context
                    }
                    request.toolReads.incrementAndGet();
                }
                final List<SourceReference> read = describeToolContext(resources);
                // Record the read pages as sources of the answer, also when the answer completed meanwhile; the
                // UI executor keeps them ahead of the completion queued after this call (issue #118).
                uiExecutor.execute(() -> {
                    if (model.getEntries().contains(request.answer)) {
                        model.mergeSources(request.answer, read);
                    }
                });
            }

            @Override
            public void onToolExecuted(final String toolName, final String arguments, final String output,
                                       final boolean succeeded) {
                // Für die Diagnose (Chat-Export): der Aufruf kommt mit der Antwort in den gespeicherten Chat.
                uiExecutor.execute(() -> {
                    if (history != null) {
                        history.toolCallTraced(conversation, request.answer, toolName, arguments, output,
                                succeeded);
                    }
                });
            }
        };
    }

    /** The chat project scope the conversation's knowledge context is valid under now (#143). */
    String knowledgeScope(ChatConversationId conversation) {
        return rag.knowledgeScope(conversation);
    }

    /** The project context of the conversation's next turn (#143). */
    EffectiveProjectContext projectContext(ChatConversationId conversation) {
        return rag.projectContext(conversation);
    }

    /** Der zuletzt verwendete Wissenskontext der Unterhaltung (für die Chat-Historie). */
    ConversationKnowledgeContext knowledgeContext(ChatConversationId conversation) {
        return rag.knowledgeContext(conversation);
    }

    /**
     * Stellt den Wissenskontext eines geöffneten Chats aus seinen gespeicherten Chunk-IDs wieder her (Indexzugriff auf
     * dem Arbeits-Executor).
     */
    void restoreKnowledgeContext(final ChatConversationId conversation, final List<String> queries,
                                 final List<KnowledgeChunkId> chunkIds, final String savedScope) {
        try {
            workExecutor.execute(() -> rag.restoreKnowledgeContext(conversation, queries, chunkIds, savedScope));
        } catch (RuntimeException rejected) {
            // Ohne Arbeits-Thread sucht die nächste Frage eben neu.
        }
    }

    /** Tell whether {@code toolName} belongs to the built-in product help. */
    static boolean isHelpTool(String toolName) {
        return AppHelpMcpTools.APP_HELP_SEARCH.equals(toolName) || AppHelpMcpTools.APP_HELP_READ.equals(toolName);
    }

    /** Überschrift der Werkzeugwolke. */
    static String toolTitle(String toolName) {
        return ReadAttachmentTool.NAME.equals(toolName) ? READING_ACTIVITY
                : SearchAttachmentTool.NAME.equals(toolName) ? SEARCHING_ACTIVITY
                : ChatBookmark.READ_TOOL.equals(toolName) ? READING_PAGE_ACTIVITY
                : KnowledgeMcpTools.KNOWLEDGE_READ_MANY.equals(toolName) ? READING_PAGES_ACTIVITY
                : KnowledgeMcpTools.KNOWLEDGE_SEARCH.equals(toolName) ? SEARCHING_KNOWLEDGE_ACTIVITY
                : ResearchDelegationTool.NAME.equals(toolName) ? RESEARCHING_KNOWLEDGE_ACTIVITY
                : AppHelpMcpTools.APP_HELP_SEARCH.equals(toolName) ? SEARCHING_HELP_ACTIVITY
                : AppHelpMcpTools.APP_HELP_READ.equals(toolName) ? READING_HELP_ACTIVITY
                : "Werkzeug " + toolName;
    }

    private static String toolKind(String toolName) {
        return ReadAttachmentTool.NAME.equals(toolName) || ChatBookmark.READ_TOOL.equals(toolName)
                || KnowledgeMcpTools.KNOWLEDGE_READ_MANY.equals(toolName)
                || AppHelpMcpTools.APP_HELP_READ.equals(toolName) ? "read"
                : SearchAttachmentTool.NAME.equals(toolName) || KnowledgeMcpTools.KNOWLEDGE_SEARCH.equals(toolName)
                || ResearchDelegationTool.NAME.equals(toolName)
                || AppHelpMcpTools.APP_HELP_SEARCH.equals(toolName) ? "search" : "other";
    }

    /**
     * Was die Wolke unter dem Titel nennt, aus den Argumenten des Aufrufs (nie aus seinem Ergebnis): den Suchbegriff,
     * bei {@code knowledge_read} den Titel einer Seite des Chats oder die Kennung, bei {@code read_attachment} den
     * Dateinamen. Unlesbare Argumente ergeben eine leere Zeile.
     */
    static String toolDetail(String toolName, String arguments, ToolSupport support, ChatConversationId conversation,
                             List<ChatBookmark> pages) {
        ToolArguments args;
        try {
            args = ToolArguments.parse(arguments);
        } catch (IllegalArgumentException unreadable) {
            return "";
        }
        if (KnowledgeMcpTools.KNOWLEDGE_SEARCH.equals(toolName) || SearchAttachmentTool.NAME.equals(toolName)
                || AppHelpMcpTools.APP_HELP_SEARCH.equals(toolName)) {
            return quoted(args.text("query"));
        }
        if (ResearchDelegationTool.NAME.equals(toolName)) {
            return quoted(args.text("task"));
        }
        if (ChatBookmark.READ_TOOL.equals(toolName)) {
            String id = args.text("id");
            if (id == null) {
                return "";
            }
            for (ChatBookmark page : pages) {
                if (page.uri().equals(id.trim())) {
                    return quoted(page.title());
                }
            }
            return quoted(id);
        }
        if (KnowledgeMcpTools.KNOWLEDGE_READ_MANY.equals(toolName)) {
            List<String> ids;
            try {
                ids = args.texts("ids");
            } catch (IllegalArgumentException unreadable) {
                return "";
            }
            return ids == null || ids.isEmpty() ? "" : ids.size() == 1 ? quoted(ids.get(0)) : ids.size() + " Seiten";
        }
        if (ReadAttachmentTool.NAME.equals(toolName)) {
            String id = args.text("attachment_id");
            if (id == null) {
                return "";
            }
            try {
                for (Attachment attachment : support.store().list(conversation)) {
                    if (attachment.id().equals(id.trim())) {
                        return quoted(attachment.fileName());
                    }
                }
            } catch (RuntimeException unavailable) {
                // ohne Liste der Anhänge nennt die Wolke die Kennung
            }
            return quoted(id);
        }
        return "";
    }

    private static String quoted(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String text = value.replace('\r', ' ').replace('\n', ' ').trim();
        return "„" + (text.length() <= 120 ? text : text.substring(0, 120) + " …") + "“";
    }

    private static String joinNames(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            text.append(text.length() == 0 ? "" : "\n").append(name);
        }
        return text.toString();
    }

    /**
     * Der Fortschritt der Wissenssuche dieser Anfrage auf ihre Recherchekarte (UI-Thread); ein Stop während der Suche
     * lässt die restlichen Suchschritte aus.
     */
    private RetrievalProgress progress(final Request request) {
        final TurnListener listener = request.listener;
        return new RetrievalProgress() {
            @Override
            public void searchStarted(final String query, final int number, final int max) {
                card(card -> card.searchStarted(query, number, max));
            }

            @Override
            public void liveSearchStarted(String query) {
                card(card -> card.step(ChatResearchCard.LIVE_SEARCH));
            }

            @Override
            public void liveHitsFound(final int hits) {
                card(card -> card.liveHitsFound(hits));
            }

            @Override
            public void pagesAvailable(final int available, final int awaited) {
                card(card -> card.pagesAvailable(available, awaited));
            }

            @Override
            public void pathStarted(final RetrievalPath path) {
                card(card -> card.step(path == RetrievalPath.KEYWORD ? ChatResearchCard.KEYWORD_SEARCH
                        : ChatResearchCard.SEMANTIC_SEARCH));
            }

            @Override
            public void hitsFound(String query, final int hits) {
                card(card -> card.hitsFound(hits));
            }

            @Override
            public void searchFailed(String query) {
                card(ChatResearchCard::searchFailed);
            }

            @Override
            public void rankingStarted(final int candidates) {
                card(card -> card.ranking(candidates));
            }

            @Override
            public void candidatesRanked(final int candidates, final int relevant) {
                card(card -> card.ranked(candidates, relevant));
            }

            @Override
            public void evidenceChecked(final int sources, int independent, final boolean sufficient) {
                card(card -> card.evidenceChecked(sources, sufficient));
            }

            @Override
            public void morePagesStarted() {
                card(card -> card.step(ChatResearchCard.MORE_PAGES));
            }

            @Override
            public void contextCheckStarted() {
                card(card -> card.step(ChatResearchCard.CHECKING));
            }

            @Override
            public void planningFinished(NextAction next) {
                listener.onUi(() -> endPlanning(request));
            }

            @Override
            public void reusingSources(final int sources) {
                card(card -> card.step(String.format(Locale.ROOT, REUSING_SOURCES_ACTIVITY, sources)));
            }

            @Override
            public void reuseInsufficient(String query, int sources) {
                card(card -> card.step(REUSE_INSUFFICIENT_ACTIVITY));
            }

            @Override
            public void contextReady(final int sources) {
                card(card -> card.contextReady(sources));
            }

            @Override
            public void funnelChanged(final KnowledgeFunnel funnel) {
                // only a card that a search opened: a turn without search shows no research card;
                // the final state arrives with attach() via the turn, so it needs no extra UI round trip
                if (funnel.phase() == FunnelPhase.DONE) {
                    return;
                }
                listener.onUi(() -> {
                    ChatResearchCard card = request.card;
                    if (card != null && card.isOpen() && !request.cancelRequested) {
                        card.funnel(funnel);
                    }
                });
            }

            @Override
            public boolean isCancelled() {
                return request.cancelled;
            }

            private void card(final Consumer<ChatResearchCard> update) {
                listener.onUi(() -> {
                    ChatResearchCard card = request.card;
                    if (card == null || request.cancelRequested) {
                        return;
                    }
                    endPlanning(request);
                    card.open();
                    if (card.isOpen()) {
                        update.accept(card);
                    }
                });
            }
        };
    }

    /** The planning bubble of {@code request} settles and disappears (UI thread); harmless when it already did. */
    private void endPlanning(Request request) {
        String key = request.planningKey;
        if (key == null) {
            return;
        }
        request.planningKey = null;
        if (model.isActivityRunning(key)) {
            model.finishActivity(key, true, "");
        }
    }

    /** Der RAG-Turn steht (UI-Thread): Quellen und Hinweise anbringen, Abbruchwunsch einlösen. */
    private void attach(Request request, RagChatTurn ragTurn, ChatConversationId conversation) {
        endPlanning(request);
        ChatTurn turn = ragTurn.turn();
        if (history != null && !ragTurn.queries().isEmpty()) {
            // Diagnose (Chat-Export): Vorabsuche und Nachsuchen des Clients stehen vor den Werkzeugaufrufen des Modells.
            history.searchesTraced(conversation, request.answer, ragTurn.queries(), ragTurn.hitCounts());
        }
        if (history != null && ragTurn.retrievalRequested()) {
            // Der Wissenskontext steht vor der Antwort fest und bleibt, auch wenn die Antwort scheitert.
            history.knowledgeContextChanged(conversation);
        }
        // Merge, never replace: sources a tool already recorded for this answer stay (issue #118).
        model.mergeSources(request.answer, describe(ragTurn.sources()));
        request.ragTurn = ragTurn;
        if (request.card != null) {
            if (request.cancelRequested && ragTurn.funnel().isPresent()) {
                // after a stop the card ends with the counts honestly reached: "Recherche abgebrochen · …"
                request.card.funnel(ragTurn.funnel().get());
            }
            request.card.finish();
        }
        // Ohne Werkzeuge steht die Wissensbeschaffung jetzt fest; mit Werkzeugen erst, wenn die Antwort fertig ist.
        if (!request.toolPath || request.ended) {
            addNotice(request);
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

    /** Der Hinweis zur Wissensbeschaffung der Anfrage, höchstens einmal (UI-Thread). */
    private void addNotice(Request request) {
        if (request.noticed || request.ragTurn == null || request.cancelRequested) {
            return; // nach einem Stop zählt die abgebrochene Suche nicht als „ohne Treffer“
        }
        request.noticed = true;
        KnowledgeAcquisition acquisition = KnowledgeAcquisition.of(request.ragTurn, request.toolReads.get(),
                request.toolReadFails.get());
        String notice = notice(acquisition);
        if (notice != null) {
            model.addNotice(notice);
        }
    }

    /**
     * Der Hinweis zur Wissensbeschaffung eines Turns oder {@code null}, wenn alles normal lief oder der Ausfall für
     * die Antwort keine Rolle spielt. Ein ausgefallener Suchpfad wird auch dann genannt, wenn der andere nichts
     * fand: so bleibt eine unvollständige Suche von einer vollständigen ohne Treffer unterscheidbar. „Ohne
     * Kontext“ steht nur da, wenn der Turn wirklich keinen Wissenskontext hatte.
     */
    static String notice(KnowledgeAcquisition acquisition) {
        boolean keyword = acquisition.failedPaths().contains(RetrievalPath.KEYWORD);
        boolean semantic = acquisition.failedPaths().contains(RetrievalPath.SEMANTIC);
        switch (acquisition.status()) {
            case RAG_PARTIALLY_FAILED:
                return keyword ? KEYWORD_PATH_FAILED_NOTICE : SEMANTIC_PATH_FAILED_NOTICE;
            case RAG_NO_HITS:
                return acquisition.toolContext() ? null : NO_SOURCES_NOTICE;
            case RAG_FAILED_TOOL_CONTEXT:
                return keyword && semantic ? RETRIEVAL_FAILED_PAGE_READ_NOTICE
                        : keyword ? KEYWORD_PATH_FAILED_PAGE_READ_NOTICE : SEMANTIC_PATH_FAILED_PAGE_READ_NOTICE;
            case NO_CONTEXT:
                if (keyword && semantic) {
                    return RETRIEVAL_FAILED_NOTICE;
                }
                if (keyword) {
                    return KEYWORD_PATH_FAILED_NOTICE + " " + NO_SOURCES_NOTICE;
                }
                if (semantic) {
                    return SEMANTIC_PATH_FAILED_NOTICE + " " + NO_SOURCES_NOTICE;
                }
                return PAGE_READ_FAILED_NOTICE;
            default:
                return null;
        }
    }

    /** Übersetzt die Quellen des Use Case in Anzeigewerte der Shell. */
    List<SourceReference> describe(List<RagSource> sources) {
        List<SourceReference> references = new ArrayList<SourceReference>(sources.size());
        for (RagSource source : sources) {
            references.add(describe(source));
        }
        return references;
    }

    /**
     * Turn the resources a tool delivered as context into display values: numbered from 1 in call order (merging
     * moves them behind the answer's existing sources), without chunk, score or ranks, located by their browser
     * address or else their resource id.
     */
    List<SourceReference> describeToolContext(List<ToolContextResource> resources) {
        List<SourceReference> references = new ArrayList<SourceReference>(resources.size());
        int number = 1;
        for (ToolContextResource resource : resources) {
            String location = resource.location().isEmpty() ? resource.resourceId() : resource.location();
            references.add(new SourceReference(number++, resource.title(), "", location,
                    revision(resource.revision()), 0d, 0, 0, resource.sourceId(), resource.resourceId(), ""));
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
                hit.semanticRank().orElse(0), source.chunk().sourceId().value(), source.chunk().resourceId().value(),
                source.chunk().id().value());
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

    /**
     * Eine Nachricht mit Suche oder Werkzeugen: ihre Antwortblase, ihr Listener, ein während der Suche geäußerter
     * Stop und was zur Wissensbeschaffung des Turns gehört (Felder ohne Atomic nur auf dem UI-Thread).
     */
    private static final class Request {
        final TranscriptEntry answer;
        final TurnListener listener;
        final boolean toolPath;
        final AtomicInteger toolReads = new AtomicInteger();
        final AtomicInteger toolReadFails = new AtomicInteger();
        /** Laufende Nummer der Anfrage für die Kennungen ihrer Wolken. */
        final int series;
        /** Die Recherchekarte, wenn die Anfrage sucht (nur UI-Thread). */
        ChatResearchCard card;
        /** The running planning bubble, {@code null} once it settled or without planning (UI thread only). */
        String planningKey;
        boolean cancelRequested;
        /** {@link #cancelRequested}, für den Thread der Suche sichtbar. */
        volatile boolean cancelled;
        RagChatTurn ragTurn;
        boolean ended;
        boolean noticed;

        Request(TranscriptEntry answer, TurnListener listener, boolean toolPath, int series) {
            this.answer = answer;
            this.listener = listener;
            this.toolPath = toolPath;
            this.series = series;
        }
    }

    /** Leitet Callbacks eines Turns auf den UI-Thread; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class TurnListener implements ChatTurnListener {

        private volatile boolean closed;
        /** Die Anfrage dieses Turns; {@code null} auf dem Weg ohne Suche und Werkzeuge. Nur UI-Thread. */
        private Request request;

        @Override
        public void onDelta(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    if (request != null) {
                        endPlanning(request);
                    }
                    if (request != null && request.card != null && !request.cancelRequested) {
                        request.card.finish(); // die Antwort beginnt: die Recherche ist vorbei
                    }
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
                    if (request != null) {
                        endPlanning(request);
                    }
                    if (request != null && request.card != null && !request.cancelRequested) {
                        // Endet der Turn vor seinem ersten Antworttext (Fehler, leere Antwort), platzt die Karte jetzt;
                        // eine ausgefallene Suche bleibt dann als Fehler stehen.
                        request.card.finish();
                    }
                    terminal.run();
                    if (request != null && request.toolPath) {
                        request.ended = true;
                        addNotice(request);
                    }
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
