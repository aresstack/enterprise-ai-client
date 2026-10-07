package com.aresstack.enterpriseai.app.ui.archfixture.tech;

import javax.swing.JPanel;

/** Gegenprobe: Swing in app-swing ist erlaubt. */
public final class SwingInsideApp {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
