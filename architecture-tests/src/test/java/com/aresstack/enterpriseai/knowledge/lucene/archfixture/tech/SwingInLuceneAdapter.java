package com.aresstack.enterpriseai.knowledge.lucene.archfixture.tech;

import javax.swing.JPanel;

/** Absichtlicher Verstoß: Swing außerhalb von app-swing/comic-controls. */
public final class SwingInLuceneAdapter {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
