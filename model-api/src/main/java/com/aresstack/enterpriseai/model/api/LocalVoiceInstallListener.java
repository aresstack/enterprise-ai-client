package com.aresstack.enterpriseai.model.api;

/** Fortschritt einer Stimmeninstallation; wird vom installierenden Thread gerufen, nie auf dem EDT. */
public interface LocalVoiceInstallListener {

    /**
     * @param file  Name der Datei, die gerade geladen wird
     * @param done  bisher geladene Bytes dieser Datei
     * @param total Gesamtgröße dieser Datei oder {@code -1}, wenn unbekannt
     */
    void progress(String file, long done, long total);
}
