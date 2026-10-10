package com.aresstack.enterpriseai.speech.runtime;

/**
 * Das Inferenz-Backend der lokalen Sprachausgabe: rechnet den Graphen einer Stimme für Token-Ids und liefert die
 * Wellenform. Stimmenformate, Textaufbereitung und WAV kennen nur dieses Interface; der Pilot ist ONNX Runtime,
 * eine eigene Inferenz-Engine ersetzt ihn hier, ohne UI, Speech-API oder Vorlesen anzufassen. Blockiert.
 */
public interface LocalSpeechRuntime extends AutoCloseable {

    /** Kurzname des Backends für Protokoll und Fehlermeldungen. */
    String name();

    /**
     * @return Samples der Wellenform in [-1, 1], mono, in der Abtastrate der Stimme
     * @throws SpeechRuntimeException wenn das Backend die Stimme nicht laden oder rechnen kann
     */
    float[] synthesize(SpeechGraphRequest request) throws SpeechRuntimeException;

    /** Gibt geladene Stimmen frei; idempotent. */
    @Override
    void close();
}
