package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Indexverzeichnis und Indexierung beim Start, wie sie der {@link IndexDialog} des Drawer-Reiters „Wissensquellen“
 * bearbeitet: die Schlüssel {@code knowledge.indexDirectory} und {@code knowledge.indexOnStartup} der
 * Konfigurationsdatei als Text und Schalter. Unveränderlich; der Dialog liefert einen neuen Stand.
 */
public final class IndexForm {

    private final String indexDirectory;
    private final boolean indexOnStartup;

    /**
     * @param indexDirectory das Verzeichnis oder leer (Standard: „index“ im Anwendungsverzeichnis); wird getrimmt
     * @param indexOnStartup ob alle angehakten Quellen beim Start im Hintergrund indexiert werden
     */
    public IndexForm(String indexDirectory, boolean indexOnStartup) {
        this.indexDirectory = indexDirectory == null ? "" : indexDirectory.trim();
        this.indexOnStartup = indexOnStartup;
    }

    /** Leer heißt Standardverzeichnis. */
    public String indexDirectory() {
        return indexDirectory;
    }

    public boolean indexOnStartup() {
        return indexOnStartup;
    }
}
