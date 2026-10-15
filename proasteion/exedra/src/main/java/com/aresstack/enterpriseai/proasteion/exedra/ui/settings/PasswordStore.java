package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.List;

/**
 * Was die Passwortverwaltung von außen braucht: Einträge auflisten, einen mit Passwort lesen, anlegen oder ändern,
 * löschen. Produktiv steht KeePassRPC dahinter; die Oberfläche kennt weder Port noch Adapter. Aufrufe kommen vom
 * EDT, Ergebnisse kommen später auf dem EDT.
 */
public interface PasswordStore {

    void list(PasswordCallback<List<PasswordEntry>> done);

    /** Der Eintrag mit Passwort; der Empfänger überschreibt es nach Gebrauch ({@link PasswordEntry#wipe()}). */
    void read(String title, PasswordCallback<PasswordEntry> done);

    /** Legt an oder ändert den Eintrag gleichen Titels; das Passwort des Eintrags wird geschrieben. */
    void save(PasswordEntry entry, PasswordCallback<Void> done);

    void remove(String title, PasswordCallback<Void> done);

    /**
     * Creates a new entry and never changes an existing one: if the title already exists (trimmed,
     * case-insensitive) the call fails with a message and KeePass stays unchanged. Without an implementation
     * nothing is written.
     */
    default void create(PasswordEntry entry, PasswordCallback<Void> done) {
        done.done(null, "Neue Einträge lassen sich hier nicht anlegen.");
    }

    /**
     * „🔗 Verbindung testen“ mit den Werten des Editors (URL, Benutzer, Passwort, Zertifikat), ohne zu speichern;
     * liefert die Erfolgsmeldung oder den Fehler.
     */
    default void testConnection(PasswordEntry entry, PasswordCallback<String> done) {
        done.done(null, "Verbindungstest in dieser Umgebung nicht verfügbar.");
    }

    /** Ohne Verdrahtung: jeder Aufruf scheitert mit derselben Meldung. */
    static PasswordStore unavailable(final String message) {
        return new PasswordStore() {
            @Override
            public void list(PasswordCallback<List<PasswordEntry>> done) {
                done.done(null, message);
            }

            @Override
            public void read(String title, PasswordCallback<PasswordEntry> done) {
                done.done(null, message);
            }

            @Override
            public void save(PasswordEntry entry, PasswordCallback<Void> done) {
                done.done(null, message);
            }

            @Override
            public void create(PasswordEntry entry, PasswordCallback<Void> done) {
                done.done(null, message);
            }

            @Override
            public void remove(String title, PasswordCallback<Void> done) {
                done.done(null, message);
            }
        };
    }
}
