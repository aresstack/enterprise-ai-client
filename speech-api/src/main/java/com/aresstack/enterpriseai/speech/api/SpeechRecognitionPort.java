package com.aresstack.enterpriseai.speech.api;

/**
 * Spracheingabe eines Modellkatalogs: macht aus einer Aufnahme Text. {@link #transcribe} blockiert (Modell laden,
 * rechnen, HTTP) und läuft deshalb nie auf dem Swing-EDT. Das Modell ist die Kennung innerhalb von
 * {@link #catalogId()} (ohne Katalogpräfix).
 */
public interface SpeechRecognitionPort {

    /** Der Katalog, aus dem die Modelle dieses Ports stammen (Präfix der Auswahl {@code model.stt}). */
    String catalogId();

    /**
     * @param modelId Kennung des STT-Modells innerhalb von {@link #catalogId()}
     * @param speech  die Aufnahme (16 kHz, mono)
     * @return der erkannte Text, getrimmt; leer, wenn nichts gesprochen wurde
     * @throws SpeechRecognitionException wenn das Modell fehlt, nicht lädt oder nicht rechnen kann
     */
    String transcribe(String modelId, RecordedSpeech speech) throws SpeechRecognitionException;
}
