package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretEntry;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;

import java.util.List;

/**
 * Der Security-Port der Anwendung, der geänderte KeePass-Einstellungen (an/aus, Host, Port, Schlüsselablage) ohne
 * Neustart übernimmt: alle Adapter halten diese eine Instanz, jede Anfrage geht an den aktuellen Provider, den
 * {@link #replace} nach dem Speichern austauscht. Hält selbst kein Secret.
 */
public final class LiveSecretProvider implements SecretProvider, com.aresstack.enterpriseai.adyton.SecretVault {

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
    public List<com.aresstack.enterpriseai.adyton.VaultEntry> list() throws SecretUnavailableException {
        return SecretVaults.of(current).list();
    }

    @Override
    public com.aresstack.enterpriseai.adyton.VaultEntryDraft read(String title)
            throws SecretUnavailableException {
        return SecretVaults.of(current).read(title);
    }

    @Override
    public void save(com.aresstack.enterpriseai.adyton.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(current).save(entry);
    }

    @Override
    public void create(com.aresstack.enterpriseai.adyton.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(current).create(entry);
    }

    @Override
    public void remove(String title) throws SecretUnavailableException {
        SecretVaults.of(current).remove(title);
    }
}
