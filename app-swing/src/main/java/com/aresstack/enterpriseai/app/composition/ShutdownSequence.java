package com.aresstack.enterpriseai.app.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Geordnetes, idempotentes Herunterfahren: Schritte laufen in Eintragungsreihenfolge, jeder genau einmal, ein
 * Fehler in einem Schritt hält die folgenden nicht auf. Wird sowohl beim Schließen des Fensters als auch vom
 * Shutdown-Hook der JVM (hartes Beenden) aufgerufen; der zweite Aufruf ist ein No-op.
 */
public final class ShutdownSequence {

    private static final Logger LOG = Logger.getLogger(ShutdownSequence.class.getName());

    private final List<Step> steps = new ArrayList<Step>();
    private final List<String> executed = new ArrayList<String>();
    private boolean started;
    private boolean finished;

    public synchronized ShutdownSequence then(String name, Runnable action) {
        if (name == null || action == null) {
            throw new IllegalArgumentException("name and action must not be null");
        }
        if (started) {
            throw new IllegalStateException("shutdown already running");
        }
        steps.add(new Step(name, action));
        return this;
    }

    /** Führt alle Schritte aus; blockiert, bis sie durch sind. Ein zweiter Aufruf kehrt sofort zurück. */
    public void run() {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
        }
        for (Step step : steps) {
            try {
                step.action.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Shutdown-Schritt \"" + step.name + "\" fehlgeschlagen: " + e, e);
            }
            synchronized (this) {
                executed.add(step.name);
            }
        }
        synchronized (this) {
            finished = true;
        }
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    /** Namen der Schritte in Reihenfolge. */
    public synchronized List<String> stepNames() {
        List<String> names = new ArrayList<String>();
        for (Step step : steps) {
            names.add(step.name);
        }
        return names;
    }

    /** Bereits ausgeführte Schritte in Reihenfolge. */
    public synchronized List<String> executedSteps() {
        return Collections.unmodifiableList(new ArrayList<String>(executed));
    }

    /** Ein Thread für {@link Runtime#addShutdownHook(Thread)}. */
    public Thread asShutdownHook() {
        Thread hook = new Thread(new Runnable() {
            @Override
            public void run() {
                ShutdownSequence.this.run();
            }
        }, "enterprise-ai-shutdown");
        hook.setDaemon(false);
        return hook;
    }

    private static final class Step {
        final String name;
        final Runnable action;

        Step(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }
    }
}
