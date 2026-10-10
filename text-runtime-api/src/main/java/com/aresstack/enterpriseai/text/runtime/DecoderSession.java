package com.aresstack.enterpriseai.text.runtime;

/**
 * Eine Generierung mit einem Decoder: hält den Zwischenspeicher der bisherigen Token (KV-Cache) und liefert je
 * Schritt die Logits der letzten Position. Nicht thread-sicher; eine Sitzung je Antwort.
 */
public interface DecoderSession extends AutoCloseable {

    /**
     * Hängt Token an (beim ersten Aufruf die ganze Eingabe, danach je ein Token) und rechnet einen Schritt.
     *
     * @return Logits über das Vokabular für die nächste Position
     */
    float[] next(long[] tokens) throws TextRuntimeException;

    /** Gibt den Zwischenspeicher frei; idempotent. */
    @Override
    void close();
}
