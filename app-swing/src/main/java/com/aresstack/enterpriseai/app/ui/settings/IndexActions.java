package com.aresstack.enterpriseai.app.ui.settings;

import java.io.IOException;
import java.util.List;

/**
 * Was der Index-Dialog ({@link IndexDialog}) des Drawer-Reiters „Wissensquellen“ von der Konfigurationsdatei
 * braucht: den Stand lesen, einen Entwurf prüfen und speichern. Die Oberfläche kennt die Datei nicht; produktiv
 * verdrahtet {@code app.settings.FileIndexActions} sie auf demselben Weg wie der Einstellungen-Dialog
 * ({@code ConfigurationFile}, Schlüssel {@code knowledge.indexDirectory} und {@code knowledge.indexOnStartup}).
 * Alle Methoden laufen auf dem EDT und berühren nur die lokale Datei, nie das Netz.
 */
public interface IndexActions {

    /** Indexverzeichnis und Häkchen, wie sie in der Datei stehen; fehlende Schlüssel mit ihrem Standardwert. */
    IndexForm current() throws IOException;

    /** Probleme des Entwurfs mit Feldnamen, nie Werte; leer, wenn er sich speichern lässt. */
    List<String> validate(IndexForm draft);

    /** Schreibt genau die beiden Schlüssel; alles andere in der Datei bleibt. Vorher ist {@link #validate} leer. */
    void save(IndexForm draft) throws IOException;
}
