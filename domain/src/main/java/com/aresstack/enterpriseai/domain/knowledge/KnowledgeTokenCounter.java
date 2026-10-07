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
     * Stabile Kennung des Zählverfahrens (Name und Version). Geht in {@link KnowledgeChunker#fingerprint()} ein;
     * eine Implementierung mit anderem Zählverhalten muss eine andere Kennung liefern. Default: Klassenname.
     */
    default String id() {
        return getClass().getName();
    }

    /**
     * Zählt jede Folge aus Buchstaben/Ziffern (Unicode, also auch Umlaute und ß) als ein Token und jedes
     * sonstige sichtbare Zeichen (Satzzeichen, Symbole) als je ein Token; Leerraum zählt nicht.
     */
    static KnowledgeTokenCounter wordsAndSymbols() {
        return new WordAndSymbolTokenCounter();
    }
}
