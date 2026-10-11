package com.aresstack.enterpriseai.app.ui.settings;

/** Rückmeldungen einer Stimmeninstallation an die Oberfläche; alle Aufrufe auf dem EDT. */
public interface LocalVoiceInstallProgress {

    /** Zwischenstand in einer Zeile, z. B. ein Arbeitsschritt ohne Datei. */
    void progress(String line);

    /**
     * Stand der Datei, die gerade geladen wird: {@code number} von {@code count} (ab 1), {@code done} von
     * {@code total} Bytes ({@code total} {@code -1}, wenn unbekannt). Ohne eigene Anzeige als Zeile.
     */
    default void file(String name, int number, int count, long done, long total) {
        progress(DownloadText.line(name, number, count, done, total));
    }

    /** Genau einmal am Ende; {@code message} nennt bei Fehlern den Grund. */
    void finished(boolean installed, String message);
}
