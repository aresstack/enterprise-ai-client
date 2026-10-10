package com.aresstack.enterpriseai.source.api;

import com.aresstack.enterpriseai.domain.source.SourceDefinition;

import java.io.IOException;
import java.util.List;

/**
 * Wo die konfigurierten Quellen liegen (produktiv die Konfigurationsdatei: {@code sources} und
 * {@code source.<id>.type}, {@code .enabled} und die Schlüssel des Adapters). Der Store prüft keine Einstellungen;
 * das tun die {@link KnowledgeSourceProvider} im Use Case.
 */
public interface SourceDefinitionStore {

    /** Die Quellen in ihrer Reihenfolge, so wie sie gespeichert sind (auch fehlerhafte). */
    List<SourceDefinition> definitions() throws IOException;

    /**
     * Speichert eine Quelle: neu hinten angefügt oder an der Stelle von {@code originalId} ersetzt (auch unter neuer
     * ID).
     *
     * @param originalId die bisherige ID beim Bearbeiten, {@code null} beim Hinzufügen
     */
    void save(SourceDefinition definition, String originalId) throws IOException;

    /** Nimmt die Quelle aus der Konfiguration. */
    void remove(String id) throws IOException;

    /** Schreibt nur das Häkchen. */
    void setEnabled(String id, boolean enabled) throws IOException;
}
