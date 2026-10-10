package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.application.speech.ReadAloudService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet den Play/Pause-Orb des Verlaufs mit dem {@link ReadAloudService}: vorgelesen wird in einem eigenen
 * Thread (nie auf dem EDT), immer nur eine Antwort; eine neue beendet die laufende. Ohne Dienst
 * ({@link #unavailable}) bleibt der Orb deaktiviert und nennt den Grund.
 */
public final class ReadAloudBinding implements ReadAloudControl {

    private static final Logger LOG = Logger.getLogger(ReadAloudBinding.class.getName());

    private final ReadAloudService service;
    private final String description;
    private final boolean autoStart;
    private final ExecutorService reader;
    /** Jede Ausgabe und jedes Stopp zählt hoch; eine wartende Ausgabe mit alter Nummer spricht nicht mehr. */
    private final AtomicLong generation = new AtomicLong();

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

    /** Keine Sprachausgabe; {@code reason} erscheint im Tooltip des deaktivierten Orbs. */
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
    public boolean autoStart() {
        return autoStart;
    }

    @Override
    public void speak(final String markdown) {
        if (service == null) {
            return;
        }
        service.stop(); // beendet eine laufende Antwort
        final long mine = generation.incrementAndGet();
        reader.execute(new Runnable() {
            @Override
            public void run() {
                if (generation.get() != mine) {
                    return; // inzwischen pausiert oder von einer neueren Antwort abgelöst
                }
                try {
                    service.speak(markdown, new ReadAloudService.Listener() {
                        @Override
                        public void failed(String message) {
                            LOG.warning("Vorlesen: " + message);
                        }
                    });
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Vorlesen fehlgeschlagen", e);
                }
            }
        });
    }

    /** Eine Bindung ändert sich nie; den Austausch meldet {@link SwitchableReadAloud}. */
    @Override
    public void addListener(Listener listener) {
        // nichts zu melden
    }

    @Override
    public void stop() {
        generation.incrementAndGet();
        if (service != null) {
            service.stop();
        }
    }

    /** Beendet das Vorlesen beim Herunterfahren; idempotent. */
    public void close() {
        if (service != null) {
            service.stop();
            reader.shutdownNow();
        }
    }
}
