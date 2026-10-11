package com.aresstack.enterpriseai.proasteion.application.security;

/**
 * Fragt den Benutzer nach dem Einmal-Passwort, das KeePass beim Pairing anzeigt. Produktiv liefert die Schale
 * (exedra) den Swing-Dialog, der selbst auf den EDT wechselt; in Tests ein Fake. Der Kompositionspunkt reicht die
 * Eingabe an den KeePassRPC-Adapter weiter, die Schale kennt dessen Typen nicht.
 */
public interface PairingPrompt {

    /**
     * @param clientDisplayName Name dieses Clients, wie KeePass ihn anzeigt
     * @return das eingegebene Passwort (der Aufrufer löscht es nach Gebrauch) oder {@code null} bei Abbruch
     */
    char[] requestPairingPassword(String clientDisplayName);
}
