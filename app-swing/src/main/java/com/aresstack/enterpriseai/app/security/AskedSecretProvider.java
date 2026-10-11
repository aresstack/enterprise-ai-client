package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.app.ui.security.CredentialDialog;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretEntry;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException.Reason;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zugang „Manuell“ statt KeePass: Referenzen {@code ask:<Name>} fragt die Anwendung beim ersten Gebrauch je Sitzung
 * ab (Benutzer und Passwort bzw. API-Key) und hält die Antwort nur im Speicher, nie in der Konfiguration. Alle
 * anderen Referenzen gehen unverändert an den Tresor (KeePass). Der Benutzer sieht das Präfix nie; der
 * Einstellungen-Dialog schreibt es, wenn „Manuell“ gewählt ist.
 */
public final class AskedSecretProvider implements SecretProvider, com.aresstack.enterpriseai.security.api.SecretVault {

    /** Präfix der manuell abgefragten Referenzen. */
    public static final String SCHEME = "ask:";

    /** Fragt Benutzer und Geheimnis ab; auf dem EDT, {@code null} bei Abbruch. */
    public interface CredentialPrompt {
        Answer ask(String label);
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
    private final Map<String, String> users = new HashMap<String, String>();
    private final Map<String, char[]> secrets = new HashMap<String, char[]>();

    public AskedSecretProvider(SecretProvider vault, CredentialPrompt prompt) {
        if (vault == null || prompt == null) {
            throw new IllegalArgumentException("vault and prompt must not be null");
        }
        this.vault = vault;
        this.prompt = prompt;
    }

    /** Produktiv: der Comic-Dialog {@link CredentialDialog}. */
    public static CredentialPrompt dialog() {
        return new CredentialPrompt() {
            @Override
            public Answer ask(String label) {
                CredentialDialog.Result result = CredentialDialog.show(label);
                return result == null ? null : new Answer(result.user(), result.password());
            }
        };
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
        synchronized (this) {
            char[] known = secrets.get(key);
            if (known != null) {
                return new SecretMaterial(ref, users.get(key), known);
            }
        }
        Answer answer = askOnEdt(ref, ref.id().substring(SCHEME.length()).trim());
        if (answer == null) {
            throw new SecretUnavailableException(Reason.CANCELLED, ref, "Eingabe der Zugangsdaten abgebrochen");
        }
        try {
            synchronized (this) {
                users.put(key, answer.user());
                secrets.put(key, Arrays.copyOf(answer.secret(), answer.secret().length));
            }
            return new SecretMaterial(ref, answer.user(), answer.secret());
        } finally {
            Arrays.fill(answer.secret(), '\0');
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

    private Answer askOnEdt(SecretRef ref, final String label) throws SecretUnavailableException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new SecretUnavailableException(Reason.NOT_AVAILABLE, ref,
                    "Manuelle Zugangsdaten brauchen ein Fenster zur Eingabe");
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return prompt.ask(label);
        }
        final AtomicReference<Answer> result = new AtomicReference<Answer>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    result.set(prompt.ask(label));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
        return result.get();
    }

    @Override
    public String toString() {
        return "AskedSecretProvider[" + vault + "]";
    }

    @Override
    public List<com.aresstack.enterpriseai.security.api.VaultEntry> list() throws SecretUnavailableException {
        return SecretVaults.of(vault).list();
    }

    @Override
    public com.aresstack.enterpriseai.security.api.VaultEntryDraft read(String title)
            throws SecretUnavailableException {
        return SecretVaults.of(vault).read(title);
    }

    @Override
    public void save(com.aresstack.enterpriseai.security.api.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(vault).save(entry);
    }

    @Override
    public void remove(String title) throws SecretUnavailableException {
        SecretVaults.of(vault).remove(title);
    }
}
