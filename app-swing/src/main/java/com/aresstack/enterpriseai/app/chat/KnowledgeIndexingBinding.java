package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.knowledge.ResourceIndexingOutcome;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Verbindet die Statuszeile der Chat-Shell mit dem {@link IndexKnowledgeUseCase} (AP22): startet die Indexierung
 * einer Quelle auf einem eigenen Thread, meldet Fortschritt und Ergebnis in das {@link KnowledgeStatusModel}
 * (auf dem UI-Thread) und gibt den Abbruchwunsch der Statuszeile über {@link IndexingListener#isCancelled()}
 * an den Lauf weiter.
 *
 * <p>Welche Quellen wann indexiert werden, entscheidet die Composition Root (AP23); sie ruft
 * {@link #indexSource} auf. Je Anbindung läuft höchstens eine Indexierung gleichzeitig. In der Statuszeile
 * stehen Zahlen, Titel und die Quell-ID, keine Fehlertexte aus Quellen oder Adaptern; die Ursache einer
 * gescheiterten Discovery (Quelle nicht erreichbar, Zugriff verweigert) geht ins Protokoll.
 */
public final class KnowledgeIndexingBinding {

    private static final Logger LOG = Logger.getLogger(KnowledgeIndexingBinding.class.getName());

    static final String START_FAILED = "Indexierung konnte nicht gestartet werden.";
    static final String DISCOVERY_FAILED_SUFFIX = " fehlgeschlagen: Die Quelle konnte nicht gelesen werden "
            + "(Ursache im Protokoll).";

    /** Statuszeile, wenn die Quelle selbst nicht lesbar war; nennt die Quelle, nicht den Fehlertext des Adapters. */
    static String discoveryFailed(String sourceId) {
        return "Indexierung von " + sourceId + DISCOVERY_FAILED_SUFFIX;
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm");

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgeStatusModel status;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    private boolean running;

    /**
     * @param uiExecutor   führt Änderungen am Status-Model auf dem UI-Thread aus
     * @param workExecutor führt die blockierende Indexierung aus (eigener Thread, nie der UI-Thread)
     * @param clock        Epoch-Millisekunden für den Zeitstempel "Stand" nach dem Lauf
     * @param zone         Zeitzone des Zeitstempels
     */
    public KnowledgeIndexingBinding(IndexKnowledgeUseCase indexing, KnowledgeStatusModel status, Executor uiExecutor,
                                    Executor workExecutor, LongSupplier clock, ZoneId zone) {
        if (indexing == null || status == null || uiExecutor == null || workExecutor == null || clock == null
                || zone == null) {
            throw new IllegalArgumentException(
                    "indexing, status, uiExecutor, workExecutor, clock and zone must not be null");
        }
        this.indexing = indexing;
        this.status = status;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.clock = clock;
        this.zone = zone;
    }

    /** Läuft gerade eine Indexierung dieser Anbindung? Nur auf dem UI-Thread aussagekräftig. */
    public boolean isRunning() {
        return running;
    }

    /**
     * Startet die Indexierung von {@code source} im {@code scope}. Muss auf dem UI-Thread gerufen werden.
     *
     * @param onDone optional; bekommt den Bericht auf dem UI-Thread, wenn der Lauf zu Ende ist
     * @return {@code false}, wenn schon eine Indexierung läuft; dann passiert nichts
     */
    public boolean indexSource(final KnowledgeSourcePort source, final SourceScope scope,
                               final Consumer<IndexingReport> onDone) {
        if (source == null || scope == null) {
            throw new IllegalArgumentException("source and scope must not be null");
        }
        if (running) {
            return false;
        }
        running = true;
        status.started("Indexierung von " + source.sourceId().value() + " …");
        try {
            workExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final IndexingReport report = indexing.indexSource(source, scope, new ProgressListener());
                    uiExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            running = false;
                            status.finished(summary(report, clock.getAsLong()));
                            if (onDone != null) {
                                onDone.accept(report);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            // Der Arbeits-Executor nimmt nichts mehr an (z. B. beim Beenden): Statuszeile und Zustand zurücksetzen,
            // damit kein Lauf als aktiv gilt, den niemand beenden kann; der Fehler bleibt beim Aufrufer.
            running = false;
            status.finished(START_FAILED);
            throw rejected;
        }
        return true;
    }

    /** Der Text nach dem Lauf: Seiten, Abschnitte, Fehlschläge, Abbruch, Zeitstempel. */
    String summary(IndexingReport report, long nowMillis) {
        if (report.discoveryFailed()) {
            LOG.warning("Indexierung von " + report.sourceId().value() + ": Quelle nicht lesbar: "
                    + report.discoveryFailure());
            return discoveryFailed(report.sourceId().value());
        }
        int indexed = report.count(IndexingStatus.INDEXED) + report.count(IndexingStatus.EMPTY);
        int failed = report.count(IndexingStatus.FAILED);
        StringBuilder text = new StringBuilder();
        if (report.isCancelled()) {
            text.append("Indexierung abgebrochen: ").append(indexed).append(" von ").append(report.discovered())
                    .append(" Seiten indexiert");
        } else {
            text.append("Wissensbasis: ").append(indexed).append(indexed == 1 ? " Seite, " : " Seiten, ")
                    .append(report.chunkCount()).append(report.chunkCount() == 1 ? " Abschnitt" : " Abschnitte");
        }
        if (failed > 0) {
            text.append(", ").append(failed).append(" fehlgeschlagen");
        }
        text.append(" (Stand ").append(STAMP.format(Instant.ofEpochMilli(nowMillis).atZone(zone))).append(')');
        return text.toString();
    }

    /** Meldet Fortschritt auf den UI-Thread und liest den Abbruchwunsch der Statuszeile. */
    private final class ProgressListener implements IndexingListener {

        private volatile int total;
        private volatile int done;

        @Override
        public void onDiscovered(List<KnowledgeResource> resources) {
            total = resources.size();
            progress("Indexierung: 0 von " + total + (total == 1 ? " Seite" : " Seiten"));
        }

        @Override
        public void onResource(ResourceIndexingOutcome outcome) {
            done++;
            String title = outcome.title().isEmpty() ? outcome.resourceId().value() : outcome.title();
            progress("Indexierung: " + done + " von " + total + (total == 1 ? " Seite" : " Seiten") + " · " + title);
        }

        @Override
        public boolean isCancelled() {
            return status.isCancelRequested();
        }

        private void progress(final String text) {
            uiExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (status.isRunning()) {
                        status.progressed(text);
                    }
                }
            });
        }
    }
}
