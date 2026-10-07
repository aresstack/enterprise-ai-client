package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Indexiert alle konfigurierten Quellen einmal nacheinander (Konfigurationsreihenfolge) über die
 * {@link KnowledgeIndexingBinding} der Shell: Jede Quelle ist ein Lauf der Statuszeile, der Abbrechen-Knopf
 * beendet den aktuellen Lauf, und danach wird keine weitere Quelle begonnen. {@link #cancel()} tut dasselbe
 * beim Beenden; {@link #awaitTermination(long, TimeUnit)} wartet, bis nichts mehr in den Index schreibt.
 *
 * <p>Die Kette läuft auf dem UI-Executor (die Binding verlangt den UI-Thread), die Arbeit selbst auf dem
 * Arbeits-Executor der Binding. Berichte landen im Log (Port-Meldungen gehören nicht in den Chat) und sind
 * über {@link #reports()} abrufbar.
 */
public final class StartupIndexing {

    private static final Logger LOG = Logger.getLogger(StartupIndexing.class.getName());

    private final KnowledgeIndexingBinding binding;
    private final KnowledgeStatusModel status;
    private final KnowledgeSourceCatalog sources;
    private final Executor uiExecutor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<IndexingReport> reports = Collections.synchronizedList(new ArrayList<IndexingReport>());
    private Iterator<KnowledgeSourceRegistration> remaining;

    public StartupIndexing(KnowledgeIndexingBinding binding, KnowledgeStatusModel status,
                           KnowledgeSourceCatalog sources, Executor uiExecutor) {
        if (binding == null || status == null || sources == null || uiExecutor == null) {
            throw new IllegalArgumentException("binding, status, sources and uiExecutor must not be null");
        }
        this.binding = binding;
        this.status = status;
        this.sources = sources;
        this.uiExecutor = uiExecutor;
    }

    /** Startet die Kette über den UI-Executor; ein zweiter Aufruf tut nichts. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        remaining = sources.registrations().iterator();
        uiExecutor.execute(new Runnable() {
            @Override
            public void run() {
                next();
            }
        });
    }

    /** Bricht den laufenden Lauf über die Statuszeile ab und beginnt keine weitere Quelle. */
    public void cancel() {
        cancelled.set(true);
        if (!started.get()) {
            return;
        }
        uiExecutor.execute(new Runnable() {
            @Override
            public void run() {
                status.requestCancel();
            }
        });
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isDone() {
        return done.getCount() == 0;
    }

    /** @return {@code true}, wenn die Kette (falls gestartet) innerhalb der Frist geendet hat */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (!started.get()) {
            return true;
        }
        return done.await(timeout, unit);
    }

    /** Berichte der abgeschlossenen Läufe in Reihenfolge. */
    public List<IndexingReport> reports() {
        return new ArrayList<IndexingReport>(reports);
    }

    /** Auf dem UI-Thread: nächste Quelle anstoßen oder die Kette beenden. */
    private void next() {
        if (cancelled.get() || !remaining.hasNext()) {
            finish();
            return;
        }
        final KnowledgeSourceRegistration registration = remaining.next();
        boolean accepted;
        try {
            accepted = binding.indexSource(registration.port(), registration.scope(), new Consumer<IndexingReport>() {
                @Override
                public void accept(IndexingReport report) {
                    reports.add(report);
                    LOG.info("Indexierung von Quelle " + report.sourceId().value() + " beendet: " + report);
                    if (report.isCancelled()) {
                        cancelled.set(true);
                    }
                    next();
                }
            });
        } catch (RuntimeException e) {
            // Arbeits-Executor nimmt nichts mehr an (Beenden): Kette beenden, nichts weiter versuchen.
            LOG.log(Level.WARNING, "Indexierung von Quelle " + registration.sourceId().value()
                    + " konnte nicht gestartet werden: " + e.getClass().getSimpleName());
            finish();
            return;
        }
        if (!accepted) {
            // Ein anderer Lauf (z. B. vom Nutzer) ist aktiv; die Startindexierung drängt sich nicht vor.
            LOG.info("Indexierung von Quelle " + registration.sourceId().value()
                    + " übersprungen: es läuft bereits eine Indexierung");
            next();
        }
    }

    private void finish() {
        done.countDown();
        LOG.info(cancelled.get() ? "Startindexierung abgebrochen" : "Startindexierung abgeschlossen ("
                + reports.size() + " Quellen)");
    }
}
