package com.aresstack.enterpriseai.app.ui.settings;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Was der Einstellungen-Dialog von außen braucht: Prüfen und Speichern des Formulars sowie die Probe des
 * KeePass-Eintrags. Die Oberfläche kennt weder Datei noch Adapter; produktiv verdrahtet
 * {@code app.settings.FileSettingsActions} die Konfigurationsdatei und {@code app.composition} den KeePass-Test.
 * Alle Methoden werden auf dem EDT gerufen; {@link #checkSecret} darf blockieren und liefert deshalb asynchron.
 */
public interface SettingsDialogActions {

    /** Probleme des Entwurfs (Schlüssel und Erwartung, nie Werte); leer, wenn er sich speichern lässt. */
    List<String> validate(SettingsForm form);

    /** Schreibt den Entwurf dauerhaft. Vorher ist {@link #validate} leer. */
    void save(SettingsForm form) throws IOException;

    /**
     * Prüft, ob der KeePass-Eintrag mit dem Titel {@code secretRef} unter den KeePass-Einstellungen des Entwurfs
     * erreichbar ist; stößt bei Bedarf das Pairing an. Das Ergebnis kommt später auf dem EDT.
     */
    void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult);
}
