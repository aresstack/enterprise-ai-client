package com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar;

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Ein Reiter (eine Seite) des Drawers. Die Anwendung selbst stellt die Seite „Chats“; über diese
 * Schnittstelle hängen weitere Seiten (Wissensquellen, später Agent-Seiten) in den Drawer — nur ein
 * Titel und eine Komponente, wie {@code ChatSidebarTab} in askai-java8 (arch).
 */
public interface ChatSidebarTab {

    /** Der Titel im Drawer und in der Reiterleiste. */
    String getTitle();

    /** Der Inhalt; wird auf dem EDT gerufen, wenn der Drawer seine Seiten (neu) aufbaut. */
    JComponent getComponent();

    /** Das Symbol in der Reiterleiste statt des Titels (der Titel wird Tooltip); {@code null} = Titel als Text. */
    default Icon getIcon() {
        return null;
    }

    /**
     * Der Beitrag dieser Seite links im gemeinsamen Fuß (z. B. „+ Quelle“), nur sichtbar, solange die Seite
     * aktiv ist; {@code null} = der linke Platz bleibt leer. Jedes Mal dieselbe Instanz liefern.
     */
    default JComponent getFooterLeadingComponent() {
        return null;
    }
}
