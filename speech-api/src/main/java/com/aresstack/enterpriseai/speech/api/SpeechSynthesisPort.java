package com.aresstack.enterpriseai.speech.api;

/**
 * Sprachausgabe eines Modellkatalogs: {@link #synthesize} blockiert (HTTP, Modell laden) und läuft deshalb nie auf
 * dem Swing-EDT. Das Modell ist die Kennung innerhalb von {@link #catalogId()} (ohne Katalogpräfix).
 */
public interface SpeechSynthesisPort {

    /** Der Katalog, aus dem die Modelle dieses Ports stammen (Präfix der Auswahl {@code model.tts}). */
    String catalogId();

    /**
     * @param modelId Kennung des TTS-Modells innerhalb von {@link #catalogId()}
     * @param text    reiner Text ohne Markdown, nicht leer
     * @return das gesprochene Audio
     * @throws SpeechSynthesisException wenn der Dienst nicht erreichbar ist oder das Modell nicht sprechen kann
     */
    SynthesizedSpeech synthesize(String modelId, String text) throws SpeechSynthesisException;

    /**
     * Wie {@link #synthesize(String, String)} mit einer festen Stimme (Sprecher) des Modells. Modelle mit mehreren
     * Sprechern wählen ohne Stimme je Anfrage selbst, sodass Absätze derselben Antwort verschieden klingen können.
     * Ports ohne Sprecherwahl (eine lokale Stimme ist genau ein Sprecher) übergehen {@code voice}.
     *
     * @param voice die Stimme des Modells; {@code null} oder leer = Standard des Ports
     */
    default SynthesizedSpeech synthesize(String modelId, String voice, String text) throws SpeechSynthesisException {
        return synthesize(modelId, text);
    }
}
