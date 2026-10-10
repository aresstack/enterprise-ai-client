package com.aresstack.enterpriseai.app.ui.workspace;

/**
 * Was der Drawer-Reiter „Wissensquellen“ auslöst. Die Oberfläche kennt weder Datei noch Index; produktiv
 * verdrahtet {@code app.knowledge.KnowledgeSourcesController} beides. Alle Aufrufe auf dem EDT.
 */
public interface KnowledgeSourceActions {

    /** Häkchen geändert: sofort für Chat und Indexierung wirksam und dauerhaft in der Datei. */
    void enabledChanged(String sourceId, boolean enabled);

    /** „Jetzt indexieren“. */
    void indexRequested(String sourceId);

    /** Bearbeiten (öffnet den Quellen-Dialog). */
    void editRequested(String sourceId);

    /**
     * Entfernen in der Zeile: nach kurzer Rückfrage verschwindet die Quelle sofort aus der Liste; sie wird aus der
     * Konfiguration genommen und ihr Index zurückgezogen (Use Case, nicht Oberfläche).
     */
    void removeRequested(String sourceId);

    /** „+ Quelle“: welcher Quelltyp, fragt der Dialog. */
    void addRequested();

    /** Ob Hinzufügen geht (ohne Konfigurationsdatei nicht). */
    boolean canAdd();
}
