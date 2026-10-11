package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.ui.chat.DictationControl;
import com.aresstack.enterpriseai.application.speech.DictationService;
import com.aresstack.enterpriseai.application.speech.SpeechRecognitionRegistry;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.speech.api.RecordedSpeech;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionException;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spracheingabe des Composers: das Modell kommt aus der Katalog-Kategorie STT ({@code model.stt}), erkannt wird über
 * den Port, den die {@link SpeechRecognitionRegistry} für den Katalog dieser Auswahl liefert (heute nur lokal im
 * Prozess). Aufnahme über Java Sound, Erkennung in einem eigenen Thread, Ergebnis auf dem EDT. Speichert der Benutzer
 * ein anderes STT-Modell, gilt es mit {@link #bind} sofort. Fehlt etwas, bleibt der Mikrofon-Knopf deaktiviert und
 * nennt den Grund.
 */
public final class Dictation implements DictationControl {

    static final String NO_MODEL = "Spracheingabe aus: kein STT-Modell gewählt (Einstellungen → Modelle → Lokale "
            + "Modelle)";
    static final String NO_PORT = "Spracheingabe aus: die Quelle des gewählten STT-Modells kann keine Sprache "
            + "erkennen (Einstellungen → Modelle → Lokale Modelle)";

    private static final Logger LOG = Logger.getLogger(Dictation.class.getName());

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "enterprise-ai-dictation");
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile DictationService service;
    private volatile String description = NO_MODEL;
    private JavaSoundRecorder recorder;
    private Result pending;
    /** Die zuletzt fehlgeschlagene Aufnahme (für „Erneut“); weg nach Erfolg, Verwerfen oder neuer Aufnahme. */
    private RecordedSpeech failedSpeech;
    /** Jede Aufnahme und jedes Verwerfen zählt hoch; ein altes Ergebnis kommt nicht mehr an. */
    private long generation;

    /** Bindet das gewählte STT-Modell (auf dem EDT); eine laufende Aufnahme endet ohne Ergebnis. */
    public void bind(ModelsConfig models, SpeechRecognitionRegistry recognition) {
        cancel();
        ModelReference selected = models.selections().get(ModelCategory.STT);
        if (selected == null) {
            service = null;
            description = NO_MODEL;
        } else if (!recognition.supports(selected.catalogId())) {
            service = null;
            description = NO_PORT;
        } else {
            service = new DictationService(recognition.require(selected.catalogId()), selected.modelId());
            description = "Modell: " + selected.modelId() + " (" + selected.catalogId() + ")";
        }
        for (Listener listener : listeners) {
            listener.dictationChanged();
        }
    }

    public String currentDescription() {
        return description;
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
    public void start(Result result) {
        cancel();
        failedSpeech = null;
        generation++;
        if (service == null) {
            result.failed(description);
            return;
        }
        try {
            recorder = JavaSoundRecorder.start();
            pending = result;
        } catch (LineUnavailableException | RuntimeException failed) {
            LOG.log(Level.WARNING, "Mikrofon nicht verfügbar", failed);
            result.failed("Mikrofon nicht verfügbar: " + failed.getMessage());
        }
    }

    @Override
    public void finish() {
        final JavaSoundRecorder active = recorder;
        final Result result = pending;
        final DictationService current = service;
        recorder = null;
        pending = null;
        if (active == null || result == null) {
            return;
        }
        if (current == null) {
            active.close();
            result.failed(description);
            return;
        }
        transcribe(active.stop(), result, current);
    }

    @Override
    public boolean canRetry() {
        return failedSpeech != null && service != null;
    }

    @Override
    public void retry(Result result) {
        RecordedSpeech speech = failedSpeech;
        DictationService current = service;
        cancel();
        if (speech == null || current == null) {
            result.failed(current == null ? description : "Keine Aufnahme zum Wiederholen");
            return;
        }
        transcribe(speech, result, current);
    }

    @Override
    public int level() {
        JavaSoundRecorder active = recorder;
        return active == null ? 0 : DecibelLevelScale.percentFromPeak(active.consumeRecentPeak());
    }

    @Override
    public boolean clipping() {
        JavaSoundRecorder active = recorder;
        return active != null && active.clipped();
    }

    /** Erkennt im Hintergrund; Ergebnis auf dem EDT, nur wenn inzwischen nichts verworfen oder neu begonnen wurde. */
    private void transcribe(final RecordedSpeech speech, final Result result, final DictationService current) {
        failedSpeech = null;
        final long expected = generation;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                String text = null;
                String problem = null;
                try {
                    long started = System.currentTimeMillis();
                    text = current.transcribe(speech);
                    LOG.info("Spracheingabe: " + speech.durationMillis() + " ms Audio in "
                            + (System.currentTimeMillis() - started) + " ms erkannt");
                } catch (SpeechRecognitionException | RuntimeException failed) {
                    LOG.log(Level.WARNING, "Spracheingabe fehlgeschlagen", failed);
                    problem = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
                } catch (OutOfMemoryError memory) {
                    LOG.log(Level.SEVERE, "Spracheingabe: zu wenig Speicher", memory);
                    problem = "zu wenig Arbeitsspeicher für das Modell";
                }
                final String recognized = text;
                final String message = problem;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (expected != generation) {
                            return;
                        }
                        if (message != null) {
                            failedSpeech = speech;
                            result.failed(message);
                        } else {
                            result.recognized(recognized);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void cancel() {
        generation++;
        if (recorder != null) {
            recorder.close();
            recorder = null;
        }
        pending = null;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** Beendet Aufnahme und Erkennung beim Herunterfahren; idempotent. */
    public void close() {
        cancel();
        worker.shutdownNow();
    }
}
