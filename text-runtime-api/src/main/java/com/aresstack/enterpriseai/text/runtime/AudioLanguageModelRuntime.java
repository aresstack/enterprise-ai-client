package com.aresstack.enterpriseai.text.runtime;

/**
 * Rechnet ein Sprachmodell mit Audio-Eingang (z. B. Gemma 4 E2B): Audio-Encoder, Token-Einbettung und Decoder mit
 * Zwischenspeicher. Merkmale (Log-Mel), Prompt, Tokenizer, Abtastung und Stopp-Regeln liegen beim Aufrufer; das
 * Backend kennt nur Merkmale, Token-Ids und Logits. Thread-sicher; die Graphen bleiben bis {@link #close()} geladen.
 * Blockiert.
 */
public interface AudioLanguageModelRuntime extends AutoCloseable {

    /** Kurzname des Backends für Protokoll und Fehlermeldungen. */
    String name();

    /** Startet eine Erkennung; lädt die Graphen beim ersten Mal. */
    AudioPromptSession open(AudioModelGraphs graphs) throws TextRuntimeException;

    /** Gibt geladene Graphen frei; idempotent. */
    @Override
    void close();
}
