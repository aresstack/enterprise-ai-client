package com.aresstack.enterpriseai.app.config;

/**
 * Anmeldung am Proxy ({@code network.proxy.auth.mode}). {@link #BASIC} ist die einzige umgesetzte Form: Benutzername
 * und Passwort kommen aus dem KeePass-Eintrag {@code network.proxy.auth.credentialRef} (Benutzername im
 * Benutzerfeld, Passwort im Passwortfeld), nie aus der Properties-Datei. Das JDK fragt die Anmeldung für den
 * HTTPS-Tunnel ({@code CONNECT}) nur über einen prozessweiten {@code java.net.Authenticator} ab; den setzt die
 * Composition Root ({@code app.security.ProxyAuthenticator}). Eine Windows-integrierte Anmeldung (NTLM/Kerberos)
 * ist nicht umgesetzt und deshalb kein Modus.
 */
public enum ProxyAuthMode {
    NONE,
    BASIC
}
