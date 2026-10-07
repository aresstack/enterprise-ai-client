package com.aresstack.enterpriseai.domain.knowledge;

/**
 * Zählt Tokens eines Texts für das Chunk-Budget. Der Default {@link #wordsAndSymbols()} ist eine
 * deterministische, modellunabhängige Schätzung; wer das Budget an einen konkreten Tokenizer koppeln will,
 * reicht eine eigene Implementierung an den {@link KnowledgeChunker}.
 *
 * <p>Der Chunker übergibt {@link #count(String)} jeweils den vollständig zusammengesetzten Kandidaten
 * (Überschriftenzeile, Trenner und Text) und vergleicht das Ergebnis mit dem Budget; Additivität wird nicht
 * vorausgesetzt.
 */
public interface KnowledgeTokenCounter {

    int count(String text);

    /**
     * Stabile, versionierte Kennung des Zählverfahrens, z. B. {@code "cl100k-v1"}. Geht in
     * {@link KnowledgeChunker#fingerprint()} ein und muss daher über JVM-Neustarts gleich bleiben (kein
     * Klassenname von Lambdas); eine Implementierung mit anderem Zählverhalten liefert eine andere Kennung.
     */
    String id();

    /**
     * Zählt jede Folge aus Buchstaben/Ziffern (Unicode, also auch Umlaute und ß) als ein Token und jedes
     * sonstige sichtbare Zeichen (Satzzeichen, Symbole) als je ein Token; Leerraum zählt nicht.
     */
    static KnowledgeTokenCounter wordsAndSymbols() {
        return new WordAndSymbolTokenCounter();
    }
}
