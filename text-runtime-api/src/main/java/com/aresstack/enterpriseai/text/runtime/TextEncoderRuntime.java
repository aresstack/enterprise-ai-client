package com.aresstack.enterpriseai.text.runtime;

import java.nio.file.Path;

/**
 * Rechnet den Encoder-Graphen eines lokalen Embedding-Modells. Das Backend legt die Eingänge nach den Namen des
 * Graphen an (Token-Ids, Maske, Segment- oder Positions-Ids) und liefert den Satzvektor, wenn der Graph einen hat,
 * sonst die Token-Vektoren. Thread-sicher; das Modell bleibt bis {@link #close()} geladen. Blockiert.
 */
public interface TextEncoderRuntime extends AutoCloseable {

    /** Kurzname des Backends für Protokoll und Fehlermeldungen. */
    String name();

    EncoderOutput encode(Path modelFile, EncoderBatch batch) throws TextRuntimeException;

    /** Gibt geladene Modelle frei; idempotent. */
    @Override
    void close();
}
