package com.aresstack.enterpriseai.app.config;

/**
 * Woher bei {@link ProxyMode#AUTO} die Adresse des PAC-/WPAD-Skripts kommt, wenn {@code network.proxy.pacUrl}
 * nicht gesetzt ist. Beides liest nur die Adresse; ausgewertet wird das Skript danach immer in der Anwendung.
 */
public enum PacDiscovery {
    /**
     * Windows-Einstellungen ohne PowerShell: {@code AutoConfigURL} in Benutzer- und Richtlinien-Hives der
     * Registry (per {@code reg.exe}), der Verbindungs-Blob {@code DefaultConnectionSettings} und das
     * WPAD-Flag. Standard.
     */
    WINDOWS_SETTINGS,
    /**
     * PowerShell-Einzeiler ({@code Get-ItemProperty … AutoConfigURL}); für Rechner, auf denen {@code reg.exe}
     * gesperrt ist, PowerShell aber erlaubt.
     */
    POWERSHELL
}
