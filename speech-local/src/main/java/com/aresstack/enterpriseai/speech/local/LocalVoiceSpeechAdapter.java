package com.aresstack.enterpriseai.speech.local;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;
import com.aresstack.enterpriseai.speech.runtime.LocalSpeechRuntime;
import com.aresstack.enterpriseai.speech.runtime.SpeechGraphRequest;
import com.aresstack.enterpriseai.speech.runtime.SpeechRuntimeException;

import java.nio.file.Path;

/**
 * {@link SpeechSynthesisPort} der lokalen Stimmen im Prozess: Modellkennung = Ordner unter dem Modellverzeichnis.
 * Liest die Stimme, macht aus dem Text Token-Ids und lässt die {@link LocalSpeechRuntime} rechnen; das Ergebnis
 * kommt als WAV. Kein Sidecar, kein externes Programm. Blockiert (Modell laden, rechnen).
 */
public final class LocalVoiceSpeechAdapter implements SpeechSynthesisPort {

    /** Obergrenze je Äußerung; das Vorlesen teilt Antworten ohnehin in Sätze. */
    static final int MAX_TOKENS = 4096;

    private final String catalogId;
    private final LocalVoiceStore voices;
    private final LocalSpeechRuntime runtime;

    /**
     * @param catalogId Katalog der lokalen Modelle (Präfix der Auswahl {@code model.tts})
     * @param modelRoot Modellverzeichnis mit einem Ordner je Stimme
     * @param runtime   Inferenz-Backend (Pilot: ONNX Runtime)
     */
    public LocalVoiceSpeechAdapter(String catalogId, Path modelRoot, LocalSpeechRuntime runtime) {
        if (catalogId == null || catalogId.trim().isEmpty() || modelRoot == null || runtime == null) {
            throw new IllegalArgumentException("catalogId, modelRoot and runtime must not be null");
        }
        this.catalogId = catalogId.trim();
        this.voices = new LocalVoiceStore(modelRoot);
        this.runtime = runtime;
    }

    @Override
    public String catalogId() {
        return catalogId;
    }

    @Override
    public SynthesizedSpeech synthesize(String modelId, String text) throws SpeechSynthesisException {
        LocalVoice voice = voices.find(modelId);
        if (voice == null) {
            throw new SpeechSynthesisException("Lokale Stimme „" + modelId + "“ ist nicht installiert oder nicht "
                    + "lesbar (Modelle → Lokale Modelle).");
        }
        long[] ids = voice.encoder().encode(text == null ? "" : text);
        if (ids.length <= 1) {
            throw new SpeechSynthesisException("Der Text enthält nichts, was die Stimme sprechen kann.");
        }
        if (ids.length > MAX_TOKENS) {
            throw new SpeechSynthesisException("Text zu lang für eine Äußerung (" + ids.length + " Token).");
        }
        VoiceParameters parameters = voice.parameters();
        try {
            float[] samples = runtime.synthesize(new SpeechGraphRequest(voice.modelFile(), ids,
                    parameters.noiseScale, parameters.lengthScale, parameters.noiseWidth, parameters.speakerId));
            return new SynthesizedSpeech(WavEncoder.encode(samples, voice.sampleRate()));
        } catch (SpeechRuntimeException e) {
            throw new SpeechSynthesisException("Lokale Stimme „" + modelId + "“ (" + runtime.name() + "): "
                    + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SpeechSynthesisException("Lokale Stimme „" + modelId + "“ (" + runtime.name() + "): " + e, e);
        }
    }
}
