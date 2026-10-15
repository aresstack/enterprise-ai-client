package com.aresstack.enterpriseai.proasteion.application.security;

/**
 * Dauerhafte, geschützte Ablage eines manuell eingegebenen API-Keys, nur auf ausdrücklichen Wunsch des Benutzers
 * („API-Key speichern“). Unabhängig von KeePass: Zugang „Manuell“ bleibt ohne KeePass nutzbar. Ein Store, der keinen
 * angemessenen Schutz bieten kann, meldet {@link #available()} {@code false} und speichert nichts; unverschlüsselt
 * wird nie geschrieben. Kein Aufruf nennt Material in Meldungen oder Logs.
 */
public interface ProtectedSecretStore {

    /** Ob der Store hier geschützt speichern kann (z. B. Windows DPAPI). */
    boolean available();

    /** Ein Satz für die Oberfläche, wie bzw. warum nicht gespeichert wird. */
    String describe();

    /** Speichert das Geheimnis zur Referenz (ersetzt ein früheres); {@code secret} löscht der Aufrufer. */
    void store(String ref, char[] secret) throws Exception;

    /** Das gespeicherte Geheimnis oder {@code null}; der Empfänger löscht es. */
    char[] load(String ref) throws Exception;

    /** Ob zur Referenz etwas gespeichert ist. */
    boolean contains(String ref);

    /** Entfernt gezielt das Geheimnis dieser Referenz; ohne Eintrag geschieht nichts. */
    void remove(String ref) throws Exception;

    /** Ein Store, der nichts speichern kann. */
    static ProtectedSecretStore unavailable(final String reason) {
        return new ProtectedSecretStore() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String describe() {
                return reason;
            }

            @Override
            public void store(String ref, char[] secret) throws Exception {
                throw new IllegalStateException(reason);
            }

            @Override
            public char[] load(String ref) {
                return null;
            }

            @Override
            public boolean contains(String ref) {
                return false;
            }

            @Override
            public void remove(String ref) {
            }

            @Override
            public String toString() {
                return "ProtectedSecretStore[unavailable]";
            }
        };
    }
}
