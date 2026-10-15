package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretEntry;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException.Reason;

import java.util.Arrays;
import java.util.List;

/**
 * Zugang „Manuell“ statt KeePass: Referenzen {@code ask:<Name>} fragt die Anwendung beim ersten Gebrauch je Sitzung
 * ab (Benutzer und Passwort bzw. API-Key) und hält die Antwort nur im Speicher, nie in der Konfiguration. Alle
 * anderen Referenzen gehen unverändert an den Tresor (KeePass). Der Benutzer sieht das Präfix nie; der
 * Einstellungen-Dialog schreibt es, wenn „Manuell“ gewählt ist.
 */
public final class AskedSecretProvider implements SecretProvider, com.aresstack.enterpriseai.adyton.SecretVault {

    /** Präfix der manuell abgefragten Referenzen. */
    public static final String SCHEME = "ask:";

    /**
     * Fragt Benutzer und Geheimnis ab, {@code null} bei Abbruch. Die Oberfläche (exedra) liefert den Dialog und
     * wechselt dafür selbst auf ihren Thread; ohne Oberfläche scheitert die Abfrage mit einer Meldung.
     */
    public interface CredentialPrompt {
        Answer ask(SecretRef ref, String label) throws Unavailable;

        /**
         * Wie {@link #ask(SecretRef, String)}, mit einem vorgemerkten, nicht geheimen Benutzernamen als Vorbelegung
         * ({@code ""}: keiner); der Benutzer kann ihn ändern oder leeren.
         */
        default Answer ask(SecretRef ref, String label, String suggestedUser) throws Unavailable {
            return ask(ref, label);
        }

        /**
         * Die Sitzung der manuellen Eingaben, die alle Zugriffe mit dieser Eingabe teilen; ohne eigene Sitzung eine
         * leere, die nur für diesen Zugriff gilt.
         */
        default ManualCredentials session() {
            return new ManualCredentials();
        }
    }

    /** Die Eingabe ist hier nicht möglich (kein Fenster); die Meldung nennt den Grund, nie Material. */
    public static final class Unavailable extends Exception {

        private static final long serialVersionUID = 1L;

        public Unavailable(String message) {
            super(message);
        }
    }

    /** Eingabe des Benutzers; das Geheimnis löscht der Empfänger. */
    public static final class Answer {
        private final String user;
        private final char[] secret;

        public Answer(String user, char[] secret) {
            this.user = user == null ? "" : user.trim();
            this.secret = secret == null ? new char[0] : secret;
        }

        String user() {
            return user;
        }

        char[] secret() {
            return secret;
        }
    }

    private final SecretProvider vault;
    private final CredentialPrompt prompt;
    private final ManualCredentials credentials;

    /** Mit der Sitzung der Eingabe ({@link CredentialPrompt#session()}): eine Eingabe gilt überall. */
    public AskedSecretProvider(SecretProvider vault, CredentialPrompt prompt) {
        this(vault, prompt, prompt == null ? null : prompt.session());
    }

    public AskedSecretProvider(SecretProvider vault, CredentialPrompt prompt, ManualCredentials credentials) {
        if (vault == null || prompt == null || credentials == null) {
            throw new IllegalArgumentException("vault, prompt and credentials must not be null");
        }
        this.vault = vault;
        this.prompt = prompt;
        this.credentials = credentials;
    }

    /** {@code true}, wenn die Referenz manuell abgefragt wird. */
    public static boolean isAsked(SecretRef ref) {
        return ref != null && ref.id().regionMatches(true, 0, SCHEME, 0, SCHEME.length());
    }

    /** Der Tresor hinter den nicht manuellen Referenzen. */
    public SecretProvider vault() {
        return vault;
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        if (!isAsked(ref)) {
            return vault.resolve(ref);
        }
        String key = ref.id();
        char[] known = credentials.secret(key);
        if (known != null) {
            try {
                return new SecretMaterial(ref, credentials.user(key), known);
            } finally {
                Arrays.fill(known, '\0');
            }
        }
        char[] stored = storedSecret(key);
        if (stored != null) {
            try {
                credentials.remember(key, credentials.suggestedUser(key), stored);
                return new SecretMaterial(ref, credentials.suggestedUser(key), stored);
            } finally {
                Arrays.fill(stored, '\0');
            }
        }
        Answer answer;
        try {
            answer = prompt.ask(ref, ref.id().substring(SCHEME.length()).trim(), credentials.suggestedUser(key));
        } catch (Unavailable e) {
            throw new SecretUnavailableException(Reason.NOT_AVAILABLE, ref, e.getMessage());
        }
        if (answer == null) {
            throw new SecretUnavailableException(Reason.CANCELLED, ref, "Eingabe der Zugangsdaten abgebrochen");
        }
        try {
            if (answer.secret().length > 0) {
                credentials.remember(key, answer.user(), answer.secret());
            }
            return new SecretMaterial(ref, answer.user(), answer.secret());
        } finally {
            Arrays.fill(answer.secret(), '\0');
        }
    }

    /** Ein ausdrücklich gespeicherter API-Key („API-Key speichern“) oder {@code null}; Fehler gelten als „keiner“. */
    private char[] storedSecret(String key) {
        ProtectedSecretStore store = credentials.persistent();
        if (!store.available() || !store.contains(key)) {
            return null;
        }
        try {
            char[] secret = store.load(key);
            return secret == null || secret.length == 0 ? null : secret;
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(AskedSecretProvider.class.getName())
                    .info("Gespeicherter API-Key nicht lesbar (" + e.getClass().getSimpleName() + "); frage neu");
            return null;
        }
    }

    @Override
    public List<SecretEntry> entries() throws SecretUnavailableException {
        return vault.entries();
    }

    @Override
    public String locationOf(SecretRef ref) throws SecretUnavailableException {
        return isAsked(ref) ? null : vault.locationOf(ref);
    }

    @Override
    public java.util.Map<String, String> attributesOf(SecretRef ref) throws SecretUnavailableException {
        return isAsked(ref) ? java.util.Collections.<String, String>emptyMap() : vault.attributesOf(ref);
    }

    @Override
    public void setAttribute(SecretRef ref, String name, String value) throws SecretUnavailableException {
        if (isAsked(ref)) {
            throw new SecretUnavailableException(Reason.NOT_AVAILABLE, ref,
                    "manuelle Zugangsdaten haben keinen KeePass-Eintrag");
        }
        vault.setAttribute(ref, name, value);
    }

    @Override
    public String toString() {
        return "AskedSecretProvider[" + vault + "]";
    }

    @Override
    public List<com.aresstack.enterpriseai.adyton.VaultEntry> list() throws SecretUnavailableException {
        return SecretVaults.of(vault).list();
    }

    @Override
    public com.aresstack.enterpriseai.adyton.VaultEntryDraft read(String title)
            throws SecretUnavailableException {
        return SecretVaults.of(vault).read(title);
    }

    @Override
    public void save(com.aresstack.enterpriseai.adyton.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(vault).save(entry);
    }

    @Override
    public void create(com.aresstack.enterpriseai.adyton.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(vault).create(entry);
    }

    @Override
    public void remove(String title) throws SecretUnavailableException {
        SecretVaults.of(vault).remove(title);
    }
}
