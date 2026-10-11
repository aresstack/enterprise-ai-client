package com.aresstack.enterpriseai.astu.acropolis.speech;

import com.aresstack.enterpriseai.speech.api.RecordedSpeech;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionException;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort;

/**
 * Spracheingabe als Anwendungsfall: eine Aufnahme mit dem gewählten STT-Modell in Text verwandeln. Zu kurze
 * Aufnahmen (ein versehentlicher Klick) gehen gar nicht erst zum Modell. Blockiert; nie auf dem Swing-EDT.
 */
public final class DictationService {

    /** Kürzere Aufnahmen erkennt kein Modell sinnvoll. */
    static final long MIN_DURATION_MILLIS = 300;

    private final SpeechRecognitionPort port;
    private final String modelId;

    public DictationService(SpeechRecognitionPort port, String modelId) {
        if (port == null || modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("port and modelId are required");
        }
        this.port = port;
        this.modelId = modelId;
    }

    /** @return der erkannte Text, getrimmt; leer bei zu kurzer Aufnahme oder Stille */
    public String transcribe(RecordedSpeech speech) throws SpeechRecognitionException {
        if (speech == null || speech.durationMillis() < MIN_DURATION_MILLIS) {
            return "";
        }
        String text = port.transcribe(modelId, speech);
        return text == null ? "" : text.trim();
    }
}
