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

    /** Bearbeiten (öffnet den Quellen-Dialog, dort auch Entfernen). */
    void editRequested(String sourceId);

    /** „+ MediaWiki“ bzw. „+ Confluence“; {@code type} ist {@code mediawiki} oder {@code confluence}. */
    void addRequested(String type);

    /** Ob Hinzufügen geht (ohne Konfigurationsdatei nicht). */
    boolean canAdd();
}
