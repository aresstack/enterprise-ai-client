package com.aresstack.enterpriseai.app.security;

/**
 * Fragt den Benutzer nach dem Einmal-Passwort, das KeePass beim Pairing anzeigt. Produktiv der Swing-Dialog
 * ({@code app.ui.security.KeePassPairingDialog}); in Tests ein Fake. Wird auf dem EDT aufgerufen.
 */
public interface PairingPrompt {

    /**
     * @param clientDisplayName Name dieses Clients, wie KeePass ihn anzeigt
     * @return das eingegebene Passwort (der Aufrufer löscht es nach Gebrauch) oder {@code null} bei Abbruch
     */
    char[] requestPairingPassword(String clientDisplayName);
}
