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
 * gewählten {@link SpeechChunking} geteilt, JEDES Stück geht sofort an einen kleinen Worker-Pool, die Wiedergabe läuft
 * strikt in Lesereihenfolge über eine durchgehende Ausgabe; kein Stück wartet mit seiner Synthese auf ein anderes
 * (wie arch). Ein neues {@link #speak} oder {@link #stop()} beendet das laufende (Generationszähler wie in arch).
 * Absätze, Sätze und Sprachabschnitte kommen wie in arch aus der {@link TextAnalysisService} (Apache OpenNLP). Ohne
 * piper.exe und ohne Windows-Stimme: es spricht die eine Stimme des gewählten TTS-Modells, auch für Abschnitte, die
 * die Spracherkennung als Englisch erkennt (je Kategorie gibt es genau ein Modell).
 */
public final class ReadAloudService {

    /** Parallele Synthese wie {@code TextToSpeechSettings.DEFAULT_SYNTHESIS_WORKERS} in arch. */
    public static final int DEFAULT_SYNTHESIS_WORKERS = 2;

    /** Obergrenze der Worker wie der Regler in arch (1 bis 8). */
    public static final int MAX_SYNTHESIS_WORKERS = 8;

    /** Sprache der Antworten, wenn die Spracherkennung nichts erkennt (die Oberfläche ist deutsch). */
    static final String FALLBACK_LANGUAGE = "de";

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
    private final TextAnalysisService analysis;
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
        this(synthesis, modelId, playback, chunking, workers, null);
    }

    /**
     * @param analysis Satz- und Spracherkennung (OpenNLP wie arch); {@code null} = einfacher Schnitt an Satzzeichen
     */
    public ReadAloudService(SpeechSynthesisPort synthesis, String modelId, AudioPlayback playback,
                            SpeechChunking chunking, int workers, TextAnalysisService analysis) {
        if (synthesis == null || modelId == null || modelId.trim().isEmpty() || playback == null) {
            throw new IllegalArgumentException("synthesis, modelId and playback must be set");
        }
        this.synthesis = synthesis;
        this.modelId = modelId;
        this.playback = playback;
        this.chunking = chunking == null ? SpeechChunking.PARAGRAPHS : chunking;
        this.workers = Math.max(1, Math.min(MAX_SYNTHESIS_WORKERS, workers));
        this.analysis = analysis;
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
        List<String> chunks = chunks(SpeechText.plainText(markdown));
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
            for (String chunk : chunks) {
                prepared.add(submit(pool, chunk));
            }
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

    /**
     * Die Stücke wie arch {@code PiperReadAloudService.segments}: die Textanalyse wird für jede Antwort angemeldet
     * (lädt bei Bedarf die Modelle) und danach wieder freigegeben; die Sprachabschnitte folgen in Lesereihenfolge.
     */
    private List<String> chunks(String plainText) {
        if (analysis == null) {
            return SpeechText.chunks(plainText, chunking);
        }
        Object token = new Object();
        analysis.acquire(token);
        try {
            List<String> chunks = new ArrayList<String>();
            for (TextAnalysisService.Segment segment : analysis.segment(plainText, FALLBACK_LANGUAGE, true,
                    chunking)) {
                for (String chunk : segment.chunks()) {
                    chunks.addAll(SpeechText.bounded(chunk));
                }
            }
            return chunks;
        } finally {
            analysis.release(token);
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
