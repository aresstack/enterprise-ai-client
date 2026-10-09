package com.aresstack.enterpriseai.app.ui.sidebar;

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
}
