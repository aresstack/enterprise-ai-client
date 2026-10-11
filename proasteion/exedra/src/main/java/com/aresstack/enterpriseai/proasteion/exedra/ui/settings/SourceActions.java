package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.source.SourceDefinition;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Was der Quellen-Dialog ({@link SourceDialog}) braucht: einen Entwurf prüfen und speichern und beim Wechsel des
 * Quelltyps einen neuen Entwurf holen. Die Oberfläche kennt weder Datei noch Adapter; produktiv steht dahinter der
 * Use Case {@code KnowledgeSourceManagement}. Alle Methoden laufen auf dem EDT und berühren nur die lokale Datei,
 * nie das Netz.
 */
public interface SourceActions {

    /**
     * Probleme des Entwurfs mit Feldnamen, nie Werte; leer, wenn er sich speichern lässt.
     *
     * @param originalAddress die bisherige Adresse beim Bearbeiten, {@code null} beim Hinzufügen
     */
    List<String> validate(SourceDefinition draft, String originalAddress);

    /**
     * Schreibt den Entwurf unter {@link #addressOf seiner Adresse} (hinzufügen oder ersetzen, auch unter neuer
     * Adresse). Vorher ist {@link #validate} leer.
     */
    void save(SourceDefinition draft, String originalAddress) throws IOException;

    /**
     * Die kanonische Adresse, unter der der Entwurf gespeichert wird: aus Typ und Endpoint abgeleitet, nie vom
     * Benutzer vergeben. Vorgabe: die Adresse des Entwurfs.
     */
    default String addressOf(SourceDefinition draft) {
        return draft.address();
    }

    /** Ein neuer Entwurf des Typs mit Vorgaben, noch ohne Adresse (Wechsel des Typs beim Hinzufügen). */
    SourceDefinition draft(String typeId);

    /**
     * Die Titel der KeePass-Einträge zur Auswahl im Feld „KeePass-Eintrag“; das Ergebnis kommt später auf dem EDT,
     * leer, wenn KeePass nicht erreichbar ist (dann bleibt das Feld eine freie Eingabe).
     */
    default void vaultEntries(Consumer<List<String>> onResult) {
        onResult.accept(Collections.<String>emptyList());
    }

    /** Die Passwortverwaltung für „➕ Neu“ am KeePass-Eintrag. */
    default PasswordStore passwordStore() {
        return PasswordStore.unavailable("In dieser Umgebung nicht verfügbar.");
    }

    /**
     * Der Zertifikat-Alias, den MainframeMate im KeePass-Eintrag ablegt ({@code MM_CertAlias}), zur Vorbelegung des
     * Felds „Zertifikat“; das Ergebnis kommt später auf dem EDT, leer, wenn der Eintrag keinen trägt oder KeePass
     * nicht erreichbar ist.
     */
    default void vaultCertificateAlias(String entryTitle, Consumer<String> onResult) {
        onResult.accept("");
    }

    /**
     * Schreibt nach dem Speichern den gewählten Zertifikat-Alias in den KeePass-Eintrag ({@code MM_CertAlias}, leer =
     * ohne Zertifikat), wie MainframeMate ihn dort ablegt; läuft im Hintergrund, ein Fehler landet im Protokoll.
     */
    default void storeVaultCertificateAlias(String entryTitle, String alias) {
    }
}
