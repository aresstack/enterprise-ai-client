package com.aresstack.enterpriseai.domain.knowledge;

/**
 * Zählt Tokens eines Texts für das Chunk-Budget. Der Default {@link #wordsAndSymbols()} ist eine
 * deterministische, modellunabhängige Schätzung; wer das Budget an einen konkreten Tokenizer koppeln will,
 * reicht eine eigene Implementierung an den {@link KnowledgeChunker}.
 *
 * <p>Der Chunker addiert die Zählungen einzelner Sätze; Implementierungen sollten daher (annähernd) additiv
 * über Leerraum-Grenzen sein.
 */
public interface KnowledgeTokenCounter {

    int count(String text);

    /**
     * Zählt jede Folge aus Buchstaben/Ziffern (Unicode, also auch Umlaute und ß) als ein Token und jedes
     * sonstige sichtbare Zeichen (Satzzeichen, Symbole) als je ein Token; Leerraum zählt nicht.
     */
    static KnowledgeTokenCounter wordsAndSymbols() {
        return new WordAndSymbolTokenCounter();
    }
}
