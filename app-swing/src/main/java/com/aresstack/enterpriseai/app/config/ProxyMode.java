package com.aresstack.enterpriseai.app.config;

/** Wie ausgehende HTTP-Verbindungen geroutet werden. */
public enum ProxyMode {
    /**
     * Standard: wie Browser und PowerShell auf demselben Rechner. Gibt es ein PAC-/WPAD-Skript (Adresse aus
     * {@code network.proxy.pacUrl} oder aus den Windows-Einstellungen, siehe {@link PacDiscovery}), entscheidet
     * das Skript je Ziel; sonst gelten die Systemeinstellungen wie bei {@link #SYSTEM}.
     */
    AUTO,
    /**
     * Nur die Proxy-Einstellungen des Betriebssystems bzw. die JVM-Properties ({@code https.proxyHost} …):
     * fester Proxy und Ausnahmen, kein PAC-Skript (Java 8 wertet keines aus).
     */
    SYSTEM,
    /** Nie über einen Proxy, auch wenn das System einen kennt. */
    NONE,
    /** Fester HTTP-Proxy aus {@code network.proxy.host}/{@code network.proxy.port}. */
    MANUAL
}
