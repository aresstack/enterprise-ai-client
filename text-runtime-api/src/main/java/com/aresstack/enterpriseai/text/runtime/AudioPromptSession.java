package com.aresstack.enterpriseai.text.runtime;

/**
 * Eine Erkennung: erst {@link #encodeAudio} (die Zahl der Audio-Token bestimmt den Prompt), dann {@link #prefill} mit
 * dem ganzen Prompt, danach {@link #next} je erzeugtem Token. Nicht thread-sicher; eine Sitzung je Aufnahme.
 */
public interface AudioPromptSession extends AutoCloseable {

    /**
     * Rechnet den Audio-Encoder.
     *
     * @param features Log-Mel-Merkmale zeilenweise ({@code frames × bins})
     * @param mask     je Rahmen, ob er echtes Audio enthält ({@code frames} Einträge)
     * @return Zahl der Audio-Token, die der Prompt als Platzhalter enthalten muss
     */
    int encodeAudio(float[] features, int frames, int bins, boolean[] mask) throws TextRuntimeException;

    /**
     * Rechnet den Prompt; die Platzhalter {@code audioTokenId} bekommen der Reihe nach die Audio-Einbettungen.
     *
     * @return Logits über das Vokabular für die nächste Position
     */
    float[] prefill(long[] tokens, long audioTokenId) throws TextRuntimeException;

    /** Hängt ein Token an und liefert die Logits der nächsten Position. */
    float[] next(long token) throws TextRuntimeException;

    /** Gibt Zwischenspeicher und Audio-Einbettungen frei; idempotent. */
    @Override
    void close();
}
