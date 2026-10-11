package com.aresstack.enterpriseai.model.api;

/** Fortschritt einer Stimmeninstallation; wird vom installierenden Thread gerufen, nie auf dem EDT. */
public interface LocalVoiceInstallListener {

    /**
     * @param file   Name der Datei, die gerade geladen wird
     * @param number laufende Nummer dieser Datei, ab 1
     * @param count  Anzahl der Dateien des Modells
     * @param done   bisher geladene Bytes dieser Datei
     * @param total  Gesamtgröße dieser Datei oder {@code -1}, wenn unbekannt
     */
    void progress(String file, int number, int count, long done, long total);
}
