package com.aresstack.enterpriseai.ui.comic.archfixture.tech;

import javax.swing.JPanel;

/** Gegenprobe: Swing in comic-controls ist erlaubt. */
public final class SwingInsideComic {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
