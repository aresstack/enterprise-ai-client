package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.application.speech.ReadAloudService;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet die Lautsprecher-Knöpfe des Verlaufs mit dem {@link ReadAloudService}: vorgelesen wird in einem eigenen
 * Thread (nie auf dem EDT), immer nur eine Antwort; ein Klick auf eine andere Antwort beendet die laufende. Ohne
 * Dienst ({@link #unavailable}) bleiben die Knöpfe deaktiviert und nennen den Grund.
 */
public final class ReadAloudBinding implements ReadAloudControl {

    private static final Logger LOG = Logger.getLogger(ReadAloudBinding.class.getName());
    private static final long NONE = Long.MIN_VALUE;

    private final ReadAloudService service;
    private final String description;
    private final boolean autoStart;
    private final ExecutorService reader;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private long readingEntry = NONE; // nur auf dem EDT

    private ReadAloudBinding(ReadAloudService service, String description, boolean autoStart) {
        this.service = service;
        this.description = description;
        this.autoStart = autoStart;
        this.reader = service == null ? null : Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "enterprise-ai-read-aloud");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /** Vorlesen mit diesem Dienst; {@code description} ist der Tooltip (Stimme). */
    public static ReadAloudBinding available(ReadAloudService service, String description, boolean autoStart) {
        if (service == null) {
            throw new IllegalArgumentException("service must not be null");
        }
        return new ReadAloudBinding(service, description, autoStart);
    }

    /** Keine Sprachausgabe; {@code reason} erscheint im Tooltip des deaktivierten Knopfs. */
    public static ReadAloudBinding unavailable(String reason) {
        return new ReadAloudBinding(null, reason, false);
    }

    @Override
    public boolean isAvailable() {
        return service != null;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean isReading(long entryId) {
        return readingEntry == entryId;
    }

    @Override
    public boolean autoStart() {
        return autoStart;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void toggle(final long entryId, final String markdown) {
        if (service == null) {
            return;
        }
        boolean wasThis = readingEntry == entryId;
        service.stop(); // beendet auch eine andere laufende Antwort
        if (wasThis) {
            readingEntry = NONE;
            fireChanged();
            return;
        }
        readingEntry = entryId;
        fireChanged();
        reader.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    service.speak(markdown, new ReadAloudService.Listener() {
                        @Override
                        public void failed(String message) {
                            LOG.warning("Vorlesen: " + message);
                        }
                    });
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Vorlesen fehlgeschlagen", e);
                } finally {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (readingEntry == entryId) {
                                readingEntry = NONE;
                                fireChanged();
                            }
                        }
                    });
                }
            }
        });
    }

    /** Beendet das Vorlesen beim Herunterfahren; idempotent. */
    public void close() {
        if (service != null) {
            service.stop();
            reader.shutdownNow();
        }
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            listener.readAloudChanged();
        }
    }
}
