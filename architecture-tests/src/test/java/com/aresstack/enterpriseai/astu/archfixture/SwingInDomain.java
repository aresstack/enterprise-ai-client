package com.aresstack.enterpriseai.astu.archfixture;

import javax.swing.JPanel;

/** Absichtlicher Verstoß: Swing im Domain-Paket. Nur für RulesDetectViolationsTest. */
public final class SwingInDomain {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
