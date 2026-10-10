package com.aresstack.enterpriseai.app.ui.settings;

import java.io.IOException;
import java.util.List;

/**
 * Was der Quellen-Dialog ({@link SourceDialog}) und der Drawer-Reiter „Wissensquellen“ von der
 * Konfigurationsdatei brauchen: die Quellen lesen, einen Entwurf prüfen und speichern, eine Quelle entfernen und
 * das Häkchen schreiben. Die Oberfläche kennt die Datei nicht; produktiv verdrahtet {@code app.settings.FileSourceActions}
 * sie. Alle Methoden laufen auf dem EDT und berühren nur die lokale Datei, nie das Netz.
 */
public interface SourceActions {

    /** Die Quellen der Datei in ihrer Reihenfolge, so wie sie dort stehen (auch fehlerhafte). */
    List<SourceForm> sources() throws IOException;

    /**
     * Probleme des Entwurfs mit Feldnamen, nie Werte; leer, wenn er sich speichern lässt.
     *
     * @param originalId die bisherige ID beim Bearbeiten, {@code null} beim Hinzufügen
     */
    List<String> validate(SourceForm draft, String originalId);

    /** Schreibt den Entwurf (hinzufügen oder ersetzen, auch unter neuer ID). Vorher ist {@link #validate} leer. */
    void save(SourceForm draft, String originalId) throws IOException;

    /** Nimmt die Quelle aus {@code sources} und kommentiert ihre Schlüssel aus. */
    void remove(String id) throws IOException;

    /** Schreibt das Häkchen: abgewählt steht als {@code source.<id>.enabled=false} in der Datei. */
    void setEnabled(String id, boolean enabled) throws IOException;
}
