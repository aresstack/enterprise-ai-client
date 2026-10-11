package com.aresstack.enterpriseai.app.ui.settings;

/** Ergebnis eines Zugriffs der Passwortverwaltung, auf dem EDT: Wert oder Fehlermeldung für den Benutzer. */
public interface PasswordCallback<T> {

    /** @param error {@code null} bei Erfolg, sonst eine Meldung ohne Secrets */
    void done(T value, String error);
}
