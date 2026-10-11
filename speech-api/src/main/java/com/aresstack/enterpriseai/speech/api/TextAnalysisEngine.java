package com.aresstack.enterpriseai.speech.api;

import java.util.List;

/**
 * Satz- und Spracherkennung für das Vorlesen (askai-java8 arch {@code ChatTextAnalysisService.Engine}): die geladenen
 * Modelle einer Engine. Fehlt ein Modell, wird nichts erfunden: ohne Spracherkennungsmodell liefert
 * {@link #detectLanguage} immer {@code ""}, ohne Satzmodell einer Sprache schneidet {@link #sentences} einfach an
 * Satzzeichen.
 */
public interface TextAnalysisEngine {

    /** Ob ein Spracherkennungsmodell geladen ist. */
    boolean detectsLanguages();

    /**
     * @return ISO-639-1 ({@code "de"} oder {@code "en"}) oder {@code ""}, wenn unbekannt, zu kurz oder kein
     * Spracherkennungsmodell geladen ist
     */
    String detectLanguage(String sentence);

    /** Die Sätze eines Absatzes in Lesereihenfolge (nie {@code null}). */
    List<String> sentences(String paragraph, String languageCode);

    /** Gibt die Modelle frei. */
    void close();
}
