package com.aresstack.enterpriseai.adyton;

import java.util.List;

/**
 * Verwaltung der Tresor-Einträge wie die Passwortverwaltung in MainframeMate ({@code ShowPasswordsMenuCommand}):
 * auflisten, einzeln mit Passwort lesen, anlegen oder ändern, löschen. Der Titel ist die eindeutige ID eines
 * Eintrags. Implementiert vom Security-Adapter neben dem {@link SecretProvider}; Aufrufe dürfen blockieren
 * (Pairing) und laufen nie auf dem Swing-EDT.
 */
public interface SecretVault {

    /** Alle Einträge ohne Passwort. */
    List<VaultEntry> list() throws SecretUnavailableException;

    /** Der Eintrag mit Passwort; der Aufrufer schließt ihn. */
    VaultEntryDraft read(String title) throws SecretUnavailableException;

    /** Legt den Eintrag an oder ändert den mit gleichem Titel; alle Felder werden ersetzt. */
    void save(VaultEntryDraft entry) throws SecretUnavailableException;

    /**
     * Legt den Eintrag nur an, wenn noch keiner mit gleichem Titel existiert (getrimmt, ohne Groß-/Kleinschreibung);
     * ein bestehender Eintrag wird nie geändert. Kollision: {@link SecretUnavailableException.Reason#ALREADY_EXISTS}.
     * Der Standard prüft den aktuellen Bestand über {@link #list()} und legt erst dann an; Adapter mit eigener
     * Sitzung prüfen und schreiben in einem Zug.
     */
    default void create(VaultEntryDraft entry) throws SecretUnavailableException {
        if (VaultTitles.containsTitle(list(), entry.title())) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.ALREADY_EXISTS, null,
                    "Eintrag existiert bereits");
        }
        save(entry);
    }

    /** Löscht den Eintrag mit diesem Titel. */
    void remove(String title) throws SecretUnavailableException;
}
