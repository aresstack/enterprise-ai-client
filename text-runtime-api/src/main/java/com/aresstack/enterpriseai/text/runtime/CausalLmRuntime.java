package com.aresstack.enterpriseai.text.runtime;

import java.nio.file.Path;

/**
 * Rechnet den Decoder-Graphen eines lokalen Chat-Modells Schritt für Schritt. Tokenizer, Chat-Vorlage, Abtastung und
 * Stopp-Regeln liegen beim Aufrufer; das Backend kennt nur Token-Ids und Logits. Thread-sicher; das Modell bleibt bis
 * {@link #close()} geladen. Blockiert.
 */
public interface CausalLmRuntime extends AutoCloseable {

    /** Kurzname des Backends für Protokoll und Fehlermeldungen. */
    String name();

    /** Startet eine Generierung; lädt das Modell beim ersten Mal. */
    DecoderSession open(Path modelFile) throws TextRuntimeException;

    /** Gibt geladene Modelle frei; idempotent. */
    @Override
    void close();
}
