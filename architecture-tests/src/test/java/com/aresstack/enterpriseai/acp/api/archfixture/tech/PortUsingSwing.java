package com.aresstack.enterpriseai.acp.api.archfixture.tech;

import javax.swing.JPanel;

/** Absichtlicher Verstoß (Nachtrag 4): acp-client-api kennt Swing. */
public final class PortUsingSwing {

    private final JPanel panel = new JPanel();

    public JPanel panel() {
        return panel;
    }
}
