package com.aresstack.enterpriseai.security.keepassrpc;

/**
 * Adapterinterne Naht zwischen {@link KeePassRpcSecretProvider} und dem Wire-Protokoll. Produktiv
 * {@link WebSocketKeePassRpcTransport}; in Tests ein Fake, damit die Fehlerabbildung ohne KeePass prüfbar ist.
 */
interface KeePassRpcTransport {

    /**
     * Führt das SRP-Pairing durch: Verbindung öffnen, anmelden (KeePass zeigt dann ein Einmal-Passwort),
     * Passwort über den Callback abfragen, Beweis senden.
     *
     * @return den neuen Sitzungsschlüssel (64 Hex-Zeichen) oder {@code null}, wenn der Benutzer abbricht
     */
    char[] pair(KeePassPairingCallback callback) throws KeePassRpcException;

    /** Öffnet eine per Key-Challenge-Response authentifizierte Sitzung mit dem gespeicherten Schlüssel. */
    Session open(char[] sessionKey) throws KeePassRpcException;

    /** Eine authentifizierte Verbindung; nach {@link #close()} unbrauchbar. */
    interface Session extends AutoCloseable {

        /**
         * Sucht einen Eintrag mit exakt diesem Titel (Groß-/Kleinschreibung egal).
         *
         * @return den Eintrag oder {@code null}, wenn keiner existiert
         */
        KeePassEntry findEntryByTitle(String title) throws KeePassRpcException;

        @Override
        void close();
    }
}
