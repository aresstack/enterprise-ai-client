package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.agent.ShellMode;

/**
 * Was die Arbeitsfläche nicht selbst kann und an die Composition Root gibt: einen neuen Chat beginnen (die
 * Anbindung eröffnet eine neue Unterhaltung und leert das Model) und die Einstellungen öffnen.
 */
public interface WorkspaceActions {

    /** „+ Neuer Chat“ im Drawer, für die gerade sichtbare Ansicht. */
    void newChatRequested(ShellMode mode);

    /** Das Zahnrad im Drawer. */
    void settingsRequested();
}
