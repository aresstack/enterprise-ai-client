package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretEntry;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.List;

/**
 * Der Security-Port der Anwendung, der geänderte KeePass-Einstellungen (an/aus, Host, Port, Schlüsselablage) ohne
 * Neustart übernimmt: alle Adapter halten diese eine Instanz, jede Anfrage geht an den aktuellen Provider, den
 * {@link #replace} nach dem Speichern austauscht. Hält selbst kein Secret.
 */
public final class LiveSecretProvider implements SecretProvider, com.aresstack.enterpriseai.security.api.SecretVault {

    private volatile SecretProvider current;

    public LiveSecretProvider(SecretProvider initial) {
        replace(initial);
    }

    public void replace(SecretProvider next) {
        if (next == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        this.current = next;
    }

    /** Der aktuelle Provider (Tests, Diagnose). */
    public SecretProvider current() {
        return current;
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        return current.resolve(ref);
    }

    @Override
    public List<SecretEntry> entries() throws SecretUnavailableException {
        return current.entries();
    }

    @Override
    public String locationOf(SecretRef ref) throws SecretUnavailableException {
        return current.locationOf(ref);
    }

    @Override
    public java.util.Map<String, String> attributesOf(SecretRef ref) throws SecretUnavailableException {
        return current.attributesOf(ref);
    }

    @Override
    public void setAttribute(SecretRef ref, String name, String value) throws SecretUnavailableException {
        current.setAttribute(ref, name, value);
    }

    @Override
    public String toString() {
        return "LiveSecretProvider[" + current + "]";
    }

    @Override
    public List<com.aresstack.enterpriseai.security.api.VaultEntry> list() throws SecretUnavailableException {
        return SecretVaults.of(current).list();
    }

    @Override
    public com.aresstack.enterpriseai.security.api.VaultEntryDraft read(String title)
            throws SecretUnavailableException {
        return SecretVaults.of(current).read(title);
    }

    @Override
    public void save(com.aresstack.enterpriseai.security.api.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(current).save(entry);
    }

    @Override
    public void remove(String title) throws SecretUnavailableException {
        SecretVaults.of(current).remove(title);
    }
}
