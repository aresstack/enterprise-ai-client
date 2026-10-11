package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.reader.ReaderLink;

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

    /** Die Bereiche (Confluence: Spaces) der Quelle auf- oder zugeklappt; beim Aufklappen werden sie geladen. */
    default void partitionsToggled(String sourceId, boolean expanded) {
    }

    /** Häkchen eines Bereichs geändert: gemerkt im Anwendungszustand, wirksam beim nächsten Indexieren. */
    default void partitionChanged(String sourceId, String key, boolean selected) {
    }

    /** „+ Quelle“: welcher Quelltyp, fragt der Dialog. */
    void addRequested();

    /** Ob Hinzufügen geht (ohne Konfigurationsdatei nicht). */
    boolean canAdd();

    /** Suchleiste: alle angehakten Quellen gleichzeitig durchsuchen; das Ergebnis kommt über {@code setSearchState}. */
    default void searchRequested(String text) {
    }

    /** Suchleiste geleert: wieder die Quellen statt der Treffer. */
    default void searchCleared() {
    }

    /** Die Trefferliste gescrollt: {@code firstVisible} ist der oberste sichtbare Treffer (Vorladen ab dort). */
    default void hitsScrolled(ReaderLink firstVisible) {
    }

    /** Eine Quelle oder ein Treffer angeklickt: im Lesebereich zeigen (Vorschau, mit {@code pin} angeheftet). */
    default void linkOpened(ReaderLink link, boolean pin) {
    }
}
