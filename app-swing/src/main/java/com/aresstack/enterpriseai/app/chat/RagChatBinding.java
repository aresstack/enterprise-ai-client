package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.SourceReference;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.chat.ChatTurn;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.application.rag.RagChatTurn;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RagOptions;
import com.aresstack.enterpriseai.application.rag.RagSource;
import com.aresstack.enterpriseai.application.rag.RetrievalPath;
import com.aresstack.enterpriseai.application.rag.RetrievalWarning;
import com.aresstack.enterpriseai.application.rag.RetrievedChunk;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

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
 *   <li><b>Stop</b> während des Retrievals merkt sich den Wunsch und bricht den Turn ab, sobald er existiert;
 *       die Nutzerfrage bleibt dabei in der Historie (AP2-Regel), die Antwortblase endet als abgebrochen.</li>
 * </ul>
 *
 * <p>Alle Model-Änderungen laufen über {@code uiExecutor} (in der Anwendung {@code SwingUtilities::invokeLater});
 * {@code sendRequested} und {@code stopRequested} müssen auf dem UI-Thread gerufen werden. In Verlauf, Quellen
 * und Hinweisen stehen nur Titel, Orte (laut Domänenregel ohne Zugangsdaten), Stände und Scores, nie Tokens oder
 * technische Fehlertexte.
 */
public final class RagChatBinding implements ChatShellActions {

    static final String RETRIEVING_ACTIVITY = "Wissen wird gesucht …";
    static final String SEND_REJECTED = "Die Nachricht konnte nicht gesendet werden.";
    static final String RETRIEVAL_FAILED_NOTICE =
            "Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.";
    static final String NO_SOURCES_NOTICE =
            "Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.";
    static final String KEYWORD_PATH_FAILED_NOTICE =
            "Die Volltextsuche ist ausgefallen. Die Quellen stammen nur aus der semantischen Suche.";
    static final String SEMANTIC_PATH_FAILED_NOTICE =
            "Die semantische Suche ist ausgefallen. Die Quellen stammen nur aus der Volltextsuche.";

    private static final DateTimeFormatter REVISION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RagChatUseCase rag;
    private final ChatConversationId conversationId;
    private final ChatShellModel model;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final ZoneId zone;
    private ChatTurn runningTurn;
    private boolean retrieving;
    private boolean cancelRequested;

    /**
     * @param uiExecutor   führt Model-Änderungen auf dem UI-Thread aus
     * @param workExecutor führt das blockierende Retrieval aus (eigener Thread, nie der UI-Thread)
     * @param zone         Zeitzone für die Anzeige des Stands einer Quelle
     */
    public RagChatBinding(RagChatUseCase rag, ChatConversationId conversationId, ChatShellModel model,
                          Executor uiExecutor, Executor workExecutor, ZoneId zone) {
        if (rag == null || conversationId == null || model == null || uiExecutor == null || workExecutor == null
                || zone == null) {
            throw new IllegalArgumentException(
                    "rag, conversationId, model, uiExecutor, workExecutor and zone must not be null");
        }
        this.rag = rag;
        this.conversationId = conversationId;
        this.model = model;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.zone = zone;
    }

    public ChatConversationId conversationId() {
        return conversationId;
    }

    /** Muss auf dem UI-Thread gerufen werden (wie alle Model-Änderungen). */
    @Override
    public void sendRequested(final String text, boolean ragEnabled) {
        if (!model.canSend(text)) {
            return;
        }
        model.addUserMessage(text);
        final TranscriptEntry answer = model.beginAssistantMessage();
        final TurnListener listener = new TurnListener();
        if (!ragEnabled) {
            startWithoutRetrieval(text, listener);
            return;
        }
        model.setAssistantActivity(RETRIEVING_ACTIVITY);
        retrieving = true;
        cancelRequested = false;
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final RagChatTurn turn;
                try {
                    turn = rag.send(conversationId, text, RagOptions.enabled(), listener);
                } catch (final RuntimeException rejected) {
                    // Use Case hat den Turn abgelehnt (z. B. Konversation beschäftigt): sichtbar machen.
                    uiExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            retrieving = false;
                            listener.closed = true;
                            model.failAssistantMessage(SEND_REJECTED);
                        }
                    });
                    return;
                }
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        attach(turn, answer, listener);
                    }
                });
            }
        });
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        ChatTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        } else if (retrieving) {
            cancelRequested = true;
        }
    }

    /** RAG aus: der bisherige Weg, synchron gestartet wie in {@link ChatServiceBinding}. */
    private void startWithoutRetrieval(String text, TurnListener listener) {
        try {
            ChatTurn turn = rag.send(conversationId, text, RagOptions.disabled(), listener).turn();
            if (!turn.isDone()) {
                runningTurn = turn;
            }
        } catch (RuntimeException rejected) {
            listener.closed = true;
            model.failAssistantMessage(SEND_REJECTED);
        }
    }

    /** Der RAG-Turn steht (UI-Thread): Quellen und Hinweise anbringen, Abbruchwunsch einlösen. */
    private void attach(RagChatTurn ragTurn, TranscriptEntry answer, TurnListener listener) {
        retrieving = false;
        ChatTurn turn = ragTurn.turn();
        if (!answer.hasSources()) {
            model.attachSources(answer, describe(ragTurn.sources()));
        }
        String notice = notice(ragTurn);
        if (notice != null) {
            model.addNotice(notice);
        }
        if (cancelRequested) {
            cancelRequested = false;
            turn.cancel();
            return;
        }
        if (!turn.isDone() && !listener.closed) {
            runningTurn = turn;
        }
    }

    /** Der Hinweis zu einem RAG-Turn oder {@code null}, wenn alles normal lief. */
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
        if (turn.sources().isEmpty()) {
            return NO_SOURCES_NOTICE;
        }
        if (failed.contains(RetrievalPath.KEYWORD)) {
            return KEYWORD_PATH_FAILED_NOTICE;
        }
        if (failed.contains(RetrievalPath.SEMANTIC)) {
            return SEMANTIC_PATH_FAILED_NOTICE;
        }
        return null;
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

        private void onUi(final Runnable update) {
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
