package com.aresstack.enterpriseai.app.ui.settings;

/** Rückmeldungen einer Stimmeninstallation an die Oberfläche; alle Aufrufe auf dem EDT. */
public interface LocalVoiceInstallProgress {

    /** Zwischenstand in einer Zeile, z. B. Datei und Prozent. */
    void progress(String line);

    /** Genau einmal am Ende; {@code message} nennt bei Fehlern den Grund. */
    void finished(boolean installed, String message);
}
