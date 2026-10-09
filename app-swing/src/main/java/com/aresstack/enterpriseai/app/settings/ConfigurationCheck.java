package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;

/**
 * Eine Prüfung über die geladene Konfiguration hinaus, die der Start ebenfalls macht und die deshalb schon im
 * Einstellungen-Dialog und beim Erststart auffallen soll, statt erst nach dem Speichern mit einem Fehlerdialog
 * und Exit-Code zu enden (z. B. die TLS-Vertrauensregel, die {@code network.tls.caCertificatesFile} liest).
 * Meldungen wie beim Loader: Schlüssel und Erwartung, nie der Wert.
 */
public interface ConfigurationCheck {

    /** @throws AppConfigException mit den Problemen, wenn die Konfiguration so nicht startfähig ist */
    void verify(AppConfig config);

    /** Keine zusätzliche Prüfung: nur der Loader entscheidet. */
    static ConfigurationCheck none() {
        return new ConfigurationCheck() {
            @Override
            public void verify(AppConfig config) {
            }
        };
    }
}
