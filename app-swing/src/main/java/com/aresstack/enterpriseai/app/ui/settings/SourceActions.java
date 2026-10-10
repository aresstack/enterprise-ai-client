package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.source.SourceDefinition;

import java.io.IOException;
import java.util.List;

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
     * @param originalId die bisherige ID beim Bearbeiten, {@code null} beim Hinzufügen
     */
    List<String> validate(SourceDefinition draft, String originalId);

    /** Schreibt den Entwurf (hinzufügen oder ersetzen, auch unter neuer ID). Vorher ist {@link #validate} leer. */
    void save(SourceDefinition draft, String originalId) throws IOException;

    /** Ein neuer Entwurf des Typs mit freier ID und Vorgaben (Wechsel des Typs beim Hinzufügen). */
    SourceDefinition draft(String typeId);
}
