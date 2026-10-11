package com.aresstack.enterpriseai.application.speech;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Vorlese-Ablauf aus askai-java8 arch ({@code PiperReadAloudService}, Batch-Pipeline): die Antwort wird in der
 * gewählten {@link SpeechChunking} geteilt, die Stücke rechnet ein kleiner Worker-Pool, die Wiedergabe läuft strikt in
 * Lesereihenfolge über eine durchgehende Ausgabe. Das erste Stück rechnet allein, damit es nicht mit den folgenden um
 * die CPU konkurriert; sobald es fertig ist, beginnt seine Wiedergabe und alle weiteren Stücke rechnen währenddessen.
 * Ein neues {@link #speak} oder {@link #stop()} beendet das laufende (Generationszähler wie in arch). Ohne piper.exe,
 * ohne Windows-Stimme und ohne NLP-Sprachtrennung: die eine Stimme ist das gewählte TTS-Modell.
 */
public final class ReadAloudService {

    /** Parallele Synthese wie {@code TextToSpeechSettings.DEFAULT_SYNTHESIS_WORKERS} in arch. */
    public static final int DEFAULT_SYNTHESIS_WORKERS = 2;

    /** Obergrenze der Worker wie der Regler in arch (1 bis 8). */
    public static final int MAX_SYNTHESIS_WORKERS = 8;

    /** Rückmeldung eines Vorlesevorgangs; läuft im Vorlese-Thread. */
    public interface Listener {

        /** Ein Stück konnte nicht gesprochen werden; {@code message} ohne Secrets. */
        void failed(String message);
    }

    private final SpeechSynthesisPort synthesis;
    private final String modelId;
    private final AudioPlayback playback;
    private final SpeechChunking chunking;
    private final int workers;
    private final Object lock = new Object();
    private long generation;

    /**
     * @param synthesis die Sprachausgabe des Katalogs, aus dem {@code modelId} stammt
     * @param modelId   das gewählte TTS-Modell (ohne Katalogpräfix)
     * @param playback  die Wiedergabe
     */
    public ReadAloudService(SpeechSynthesisPort synthesis, String modelId, AudioPlayback playback) {
        this(synthesis, modelId, playback, SpeechChunking.PARAGRAPHS, DEFAULT_SYNTHESIS_WORKERS);
    }

    /**
     * @param chunking Einheit je Synthese (Standard Absätze)
     * @param workers  parallele Synthesen, 1 bis {@link #MAX_SYNTHESIS_WORKERS}
     */
    public ReadAloudService(SpeechSynthesisPort synthesis, String modelId, AudioPlayback playback,
                            SpeechChunking chunking, int workers) {
        if (synthesis == null || modelId == null || modelId.trim().isEmpty() || playback == null) {
            throw new IllegalArgumentException("synthesis, modelId and playback must be set");
        }
        this.synthesis = synthesis;
        this.modelId = modelId;
        this.playback = playback;
        this.chunking = chunking == null ? SpeechChunking.PARAGRAPHS : chunking;
        this.workers = Math.max(1, Math.min(MAX_SYNTHESIS_WORKERS, workers));
    }

    public String modelId() {
        return modelId;
    }

    /**
     * Liest eine Markdown-Antwort vor; blockiert bis zum Ende oder bis {@link #stop()} (nie auf dem EDT rufen).
     *
     * @return {@code true}, wenn mindestens ein Stück erklungen ist (auch wenn danach gestoppt wurde)
     */
    public boolean speak(String markdown, Listener listener) {
        final long myGeneration;
        synchronized (lock) {
            myGeneration = ++generation;
        }
        List<String> chunks = SpeechText.chunks(SpeechText.plainText(markdown), chunking);
        if (chunks.isEmpty()) {
            return false;
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "enterprise-ai-tts-worker");
                thread.setDaemon(true);
                return thread;
            }
        });
        List<Future<byte[]>> prepared = new ArrayList<Future<byte[]>>();
        try {
            prepared.add(submit(pool, chunks.get(0)));
            boolean anySpoken = false;
            String reported = null;
            for (int i = 0; i < chunks.size(); i++) {
                if (!isCurrent(myGeneration)) {
                    return anySpoken;
                }
                Future<byte[]> future = prepared.get(i);
                byte[] wav;
                try {
                    wav = future.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return anySpoken;
                } catch (ExecutionException failed) {
                    reported = report(listener, reported, message(failed.getCause()));
                    wav = null;
                } finally {
                    if (i == 0) {
                        // das erste Stück ist fertig: alle weiteren rechnen, während es klingt
                        for (int next = 1; next < chunks.size(); next++) {
                            prepared.add(submit(pool, chunks.get(next)));
                        }
                    }
                }
                if (wav == null) {
                    continue;
                }
                if (!isCurrent(myGeneration)) {
                    return anySpoken;
                }
                try {
                    playback.play(wav);
                    anySpoken = true;
                } catch (IOException failedPlayback) {
                    reported = report(listener, reported, "Wiedergabe fehlgeschlagen: " + message(failedPlayback));
                }
            }
            return anySpoken;
        } finally {
            pool.shutdownNow();
            playback.finish();
        }
    }

    private Future<byte[]> submit(ExecutorService pool, final String chunk) {
        return pool.submit(new Callable<byte[]>() {
            @Override
            public byte[] call() throws SpeechSynthesisException {
                return synthesis.synthesize(modelId, chunk).wav();
            }
        });
    }

    /** Beendet das laufende Vorlesen sofort (die laufende Wiedergabe bricht ab, weitere Stücke entfallen). */
    public void stop() {
        synchronized (lock) {
            generation++;
        }
        playback.stop();
    }

    private boolean isCurrent(long myGeneration) {
        synchronized (lock) {
            return generation == myGeneration;
        }
    }

    /** Meldet jede Fehlermeldung eines Vorgangs nur einmal hintereinander. */
    private static String report(Listener listener, String previous, String message) {
        if (listener != null && !message.equals(previous)) {
            listener.failed(message);
        }
        return message;
    }

    private static String message(Throwable failure) {
        if (failure == null) {
            return "unbekannter Fehler";
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
