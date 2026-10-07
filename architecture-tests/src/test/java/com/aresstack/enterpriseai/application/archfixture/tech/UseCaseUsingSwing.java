package com.aresstack.enterpriseai.application.archfixture.tech;

import javax.swing.JLabel;

/** Absichtlicher Verstoß: application kennt Swing. */
public final class UseCaseUsingSwing {

    private final JLabel label = new JLabel();

    public JLabel label() {
        return label;
    }
}
