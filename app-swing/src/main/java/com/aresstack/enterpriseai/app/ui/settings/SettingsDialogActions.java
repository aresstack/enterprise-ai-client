package com.aresstack.enterpriseai.app.ui.settings;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Was der Einstellungen-Dialog von außen braucht: Prüfen und Speichern des Formulars sowie die Probe des
 * KeePass-Eintrags und den Verbindungstest gegen den KI-Dienst. Die Oberfläche kennt weder Datei noch Adapter;
 * produktiv verdrahtet {@code app.settings.FileSettingsActions} die Konfigurationsdatei und
 * {@code app.composition} KeePass-Probe und Verbindungstest. Alle Methoden werden auf dem EDT gerufen;
 * {@link #checkSecret} und {@link #checkConnection} dürfen blockieren und liefern deshalb asynchron.
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

    /**
     * Prüft mit dem Entwurf Schritt für Schritt den Weg zum KI-Dienst (Proxy-Route, Namensauflösung, API-Key aus
     * KeePass, TLS, {@code GET /models}) und meldet jeden Schritt, sobald er feststeht, danach genau einmal
     * {@link ConnectionCheckListener#onFinished}; alles auf dem EDT. Ohne Verdrahtung meldet die Vorgabe, dass der
     * Test hier nicht verfügbar ist.
     */
    default void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
        listener.onStep(ConnectionCheckStep.failed("Verbindungstest",
                "In dieser Umgebung nicht verfügbar."));
        listener.onFinished(false);
    }
}
