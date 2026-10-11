package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.awt.Component;

/**
 * Öffentlicher Zugang zur Zertifikatsauswahl ({@link CertificateChooser}, aus MainframeMate) für Oberflächen
 * außerhalb dieses Pakets, etwa den Einrichtungsassistenten. Gleiche Tabelle, gleiche Vorauswahl.
 */
public final class CertificatePicker {

    private CertificatePicker() {
    }

    /** @return der gewählte Alias oder {@code null} bei Abbruch; nur auf dem EDT */
    public static String choose(Component parent, String currentAlias) {
        return CertificateChooser.showChooserDialog(parent, currentAlias);
    }
}
