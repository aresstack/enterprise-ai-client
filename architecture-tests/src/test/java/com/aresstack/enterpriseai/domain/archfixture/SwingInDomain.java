package com.aresstack.enterpriseai.domain.archfixture;

import javax.swing.JPanel;

/** Absichtlicher Verstoß: Swing im Domain-Paket. Nur für RulesDetectViolationsTest. */
public final class SwingInDomain {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
