package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.astu.acropolis.speech.ReadAloudService;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRate;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet den Play/Pause-Orb des Verlaufs mit dem {@link ReadAloudService}: vorgelesen wird in einem eigenen
 * Thread (nie auf dem EDT), immer nur eine Antwort; eine neue bricht die laufende sofort ab, auch wenn diese noch auf
 * ein Remote-Stück wartet, statt hinter ihr anzustehen (der neueste Satz gewinnt). Ohne Dienst
 * ({@link #unavailable}) bleibt der Orb deaktiviert und nennt den Grund.
 */
public final class ReadAloudBinding implements ReadAloudControl {

    private static final Logger LOG = Logger.getLogger(ReadAloudBinding.class.getName());

    private final ReadAloudService service;
    private final String description;
    /** Katalog für die Diagnose je Stück ({@code catalog=…}), ohne Secrets. */
    private final String traceContext;
    private final boolean autoStart;
    private final ExecutorService reader;
    /** Jede Ausgabe und jedes Stopp zählt hoch; eine wartende Ausgabe mit alter Nummer spricht nicht mehr. */
    private final AtomicLong generation = new AtomicLong();
    /** Die laufende oder wartende Ausgabe; {@link #speak}, {@link #stop()} und {@link #close()} brechen sie ab. */
    private Read active;

    private ReadAloudBinding(ReadAloudService service, String description, boolean autoStart, String traceContext) {
        this.service = service;
        this.description = description;
        this.traceContext = traceContext;
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
        return available(service, description, autoStart, "");
    }

    /**
     * @param traceContext Katalog für die Diagnosezeile je Stück, z. B. {@code catalog=kipitz}
     */
    public static ReadAloudBinding available(ReadAloudService service, String description, boolean autoStart,
                                             String traceContext) {
        if (service == null) {
            throw new IllegalArgumentException("service must not be null");
        }
        return new ReadAloudBinding(service, description, autoStart, traceContext == null ? "" : traceContext);
    }

    /** Keine Sprachausgabe; {@code reason} erscheint im Tooltip des deaktivierten Orbs. */
    public static ReadAloudBinding unavailable(String reason) {
        return new ReadAloudBinding(null, reason, false, "");
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
        speak(markdown, null);
    }

    @Override
    public void speak(final String markdown, final Runnable finished) {
        if (service == null) {
            done(finished);
            return;
        }
        service.stop(); // beendet eine laufende Antwort
        final long mine = generation.incrementAndGet();
        final Read read = new Read(finished);
        synchronized (this) {
            cancelActive();
            active = read;
            read.future = reader.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        read(mine, markdown);
                    } finally {
                        read.finish();
                    }
                }
            });
        }
    }

    /** Bricht die aktive Ausgabe ab: ein Warten auf ein Remote-Stück endet per Interrupt, ihr Ende wird gemeldet. */
    private synchronized void cancelActive() {
        Read previous = active;
        active = null;
        if (previous != null) {
            previous.cancel();
        }
    }

    /** Meldet das Ende einer Ausgabe auf dem EDT. */
    private static void done(Runnable finished) {
        if (finished != null) {
            SwingUtilities.invokeLater(finished);
        }
    }

    /** Eine Ausgabe mit ihrem Abschluss; das Ende meldet sie genau einmal, ob fertig gelesen oder abgebrochen. */
    private static final class Read {

        private final Runnable finished;
        private final AtomicBoolean reported = new AtomicBoolean();
        private Future<?> future;

        Read(Runnable finished) {
            this.finished = finished;
        }

        void cancel() {
            if (future != null) {
                future.cancel(true);
            }
            finish(); // auch eine nie gestartete Ausgabe meldet ihr Ende
        }

        void finish() {
            if (reported.compareAndSet(false, true)) {
                done(finished);
            }
        }
    }

    private void read(long mine, String markdown) {
        if (generation.get() != mine) {
            return; // inzwischen pausiert oder von einer neueren Antwort abgelöst
        }
        final boolean[] reported = new boolean[1]; // eine Meldung je Vorlesen, nicht je Abschnitt
        try {
            service.speak(markdown, new ReadAloudService.Listener() {
                @Override
                public void failed(String message) {
                    LOG.warning("Vorlesen: " + message);
                    if (!reported[0]) {
                        reported[0] = true;
                        showFailure(message);
                    }
                }

                @Override
                public void chunkRequested(ReadAloudService.ChunkTrace trace) {
                    LOG.info(requestLine(trace, traceContext));
                }

                @Override
                public void chunkSynthesized(ReadAloudService.ChunkTrace trace,
                                             ReadAloudService.WavFormat wav) {
                    LOG.info(audioLine(trace, wav));
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Vorlesen fehlgeschlagen", e);
            if (!reported[0]) {
                showFailure(e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Die Diagnosezeile vor dem Request eines Stücks: Vorgang, Stück, Katalog, Modell, Stimme, Worker. Nie Text oder
     * Secrets; der Worker-Name taucht auch in der Zeile des Adapters auf.
     */
    static String requestLine(ReadAloudService.ChunkTrace trace, String traceContext) {
        StringBuilder line = new StringBuilder("TTS request: generation=").append(trace.generation())
                .append(" chunk=").append(trace.index()).append('/').append(trace.count());
        if (traceContext != null && !traceContext.isEmpty()) {
            line.append(' ').append(traceContext);
        }
        return line.append(" model=").append(trace.modelId()).append(" voice=")
                .append(trace.voice() == null ? "none" : trace.voice()).append(" worker=").append(trace.worker())
                .append(" chars=").append(trace.characters()).toString();
    }

    /** Die Diagnosezeile nach dem Audio eines Stücks: dasselbe Stück mit dem Format, das zurückkam. */
    static String audioLine(ReadAloudService.ChunkTrace trace, ReadAloudService.WavFormat wav) {
        return "TTS audio: generation=" + trace.generation() + " chunk=" + trace.index() + "/" + trace.count()
                + " model=" + trace.modelId() + " voice=" + (trace.voice() == null ? "none" : trace.voice())
                + " worker=" + trace.worker() + " wav=" + wav;
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
            cancelActive();
        }
    }

    @Override
    public boolean canPause() {
        return service != null;
    }

    @Override
    public void pause() {
        if (service != null) {
            service.pause();
        }
    }

    @Override
    public void resume() {
        if (service != null) {
            service.resume();
        }
    }

    @Override
    public boolean isPaused() {
        return service != null && service.isPaused();
    }

    @Override
    public void setRate(double rate) {
        if (service != null) {
            service.setRate(rate);
        }
    }

    @Override
    public double rate() {
        return service == null ? SpeechRate.NORMAL : service.rate();
    }

    /** Ein Fehler beim Vorlesen verpufft nicht still: kurze Meldung mit dem Grund (EDT). */
    private void showFailure(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null, "Der Text konnte nicht vorgelesen werden.\n" + message
                        + "\n\n" + description, "Vorlesen", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    /** Beendet das Vorlesen beim Herunterfahren; idempotent. */
    public void close() {
        if (service != null) {
            service.stop();
            cancelActive();
            reader.shutdownNow();
        }
    }
}
