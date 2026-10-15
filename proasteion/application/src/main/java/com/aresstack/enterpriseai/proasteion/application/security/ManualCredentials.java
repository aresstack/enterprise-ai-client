package com.aresstack.enterpriseai.proasteion.application.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Zugangsdaten „Manuell“ ({@code ask:<Name>}) einer Sitzung, geteilt von allen {@link AskedSecretProvider}n des
 * Prozesses: Was der Benutzer einmal eingibt (im Abfrage-Dialog oder im Einrichtungsassistenten), gilt für
 * Verbindungstest, Modellabfrage, Abschlussprüfung und Anwendung gleichermaßen, ohne zweite Abfrage. Alles liegt nur
 * im Speicher; dauerhaft ist allein ein ausdrücklich gespeicherter API-Key im {@link ProtectedSecretStore} und ein
 * vorgemerkter, nicht geheimer Benutzername.
 *
 * <p>Schlüssel ist die Referenz ohne Rücksicht auf Groß-/Kleinschreibung des Präfixes; jede Quelle hat ihre eigene
 * Referenz, damit sich Benutzernamen mehrerer Quellen nicht mischen.
 */
public final class ManualCredentials {

    /**
     * Quelleinstellung mit dem vorgemerkten, nicht geheimen Benutzernamen einer Quelle mit Zugang „Manuell“ (nie
     * Passwort oder Token).
     */
    public static final String SOURCE_USER_SETTING = "manualUserName";

    private final Map<String, String> users = new HashMap<String, String>();
    private final Map<String, char[]> secrets = new HashMap<String, char[]>();
    /** Vorgemerkte Benutzernamen (Vorbelegung im Dialog), nie ein Secret. */
    private final Map<String, String> suggested = new HashMap<String, String>();
    /** Die ausdrücklich gewählte dauerhafte Ablage („API-Key speichern“); ohne Wahl speichert nichts. */
    private volatile ProtectedSecretStore persistent =
            ProtectedSecretStore.unavailable("Dauerhaftes Speichern ist hier nicht eingerichtet.");

    /** Eine leere Sitzung; die Anwendung legt beim Start genau eine an und reicht sie über die Eingaben weiter. */
    public ManualCredentials() {
    }

    /** {@code true}, wenn die Referenz manuell abgefragt wird ({@code ask:…}). */
    public static boolean isManual(String ref) {
        String id = ref == null ? "" : ref.trim();
        return id.regionMatches(true, 0, AskedSecretProvider.SCHEME, 0, AskedSecretProvider.SCHEME.length());
    }

    /** Der Name im Abfrage-Dialog: die Referenz ohne Präfix. */
    public static String label(String ref) {
        String id = ref == null ? "" : ref.trim();
        return isManual(id) ? id.substring(AskedSecretProvider.SCHEME.length()).trim() : id;
    }

    private static String key(String ref) {
        if (!isManual(ref)) {
            throw new IllegalArgumentException("not a manual reference");
        }
        return AskedSecretProvider.SCHEME + label(ref).toLowerCase(Locale.ROOT);
    }

    /** Merkt Benutzer und Geheimnis für diese Sitzung; {@code secret} bleibt beim Aufrufer (er löscht es). */
    public synchronized void remember(String ref, String user, char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        String key = key(ref);
        wipe(secrets.remove(key));
        users.put(key, user == null ? "" : user.trim());
        secrets.put(key, Arrays.copyOf(secret, secret.length));
    }

    /** Vergisst die Eingabe dieser Sitzung (z. B. nach einem gescheiterten Test); das Geheimnis wird überschrieben. */
    public synchronized void forget(String ref) {
        String key = key(ref);
        users.remove(key);
        wipe(secrets.remove(key));
    }

    /** {@code true}, wenn in dieser Sitzung ein Geheimnis für die Referenz eingegeben wurde. */
    public synchronized boolean knows(String ref) {
        return isManual(ref) && secrets.containsKey(key(ref));
    }

    /** Der Benutzer der Eingabe dieser Sitzung, {@code ""} ohne Eingabe. */
    public synchronized String user(String ref) {
        String user = isManual(ref) ? users.get(key(ref)) : null;
        return user == null ? "" : user;
    }

    /** Eine Kopie des Geheimnisses dieser Sitzung oder {@code null}; der Empfänger löscht sie. */
    public synchronized char[] secret(String ref) {
        char[] known = isManual(ref) ? secrets.get(key(ref)) : null;
        return known == null ? null : Arrays.copyOf(known, known.length);
    }

    /** Merkt einen nicht geheimen Benutzernamen als Vorbelegung des Dialogs; leer entfernt die Vorbelegung. */
    public synchronized void suggestUser(String ref, String user) {
        String key = key(ref);
        String value = user == null ? "" : user.trim();
        if (value.isEmpty()) {
            suggested.remove(key);
        } else {
            suggested.put(key, value);
        }
    }

    /** Die Vorbelegung des Benutzernamens im Dialog, {@code ""} ohne. */
    public synchronized String suggestedUser(String ref) {
        String user = isManual(ref) ? suggested.get(key(ref)) : null;
        return user == null ? "" : user;
    }

    /** Die dauerhafte Ablage für ausdrücklich gespeicherte API-Keys (einmal beim Start gesetzt). */
    public void persistent(ProtectedSecretStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.persistent = store;
    }

    public ProtectedSecretStore persistent() {
        return persistent;
    }

    /** Vergisst alles (Tests). */
    public synchronized void clear() {
        for (char[] secret : secrets.values()) {
            wipe(secret);
        }
        secrets.clear();
        users.clear();
        suggested.clear();
    }

    private static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }

    @Override
    public String toString() {
        return "ManualCredentials[***]";
    }
}
