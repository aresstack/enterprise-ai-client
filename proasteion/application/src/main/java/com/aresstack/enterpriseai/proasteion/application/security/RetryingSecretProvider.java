package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.adyton.SecretEntry;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretVault;
import com.aresstack.enterpriseai.adyton.VaultEntry;
import com.aresstack.enterpriseai.adyton.VaultEntryDraft;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Wiederholt lesende Zugriffe auf den Security-Port, wenn das Backend vorübergehend nicht erreichbar ist
 * ({@link SecretUnavailableException.Reason#NOT_AVAILABLE}: KeePassRPC-Timeout, kurz abgebrochene Verbindung).
 * Ein fehlender Eintrag, ein abgelehnter Zugriff und ein abgebrochenes Pairing werden nie wiederholt; ein
 * Verbindungsfehler löst kein neues Pairing aus, weil die Wiederholung nur den Aufruf des Adapters erneut macht.
 * Schreibende Zugriffe ({@link #setAttribute}, {@link #save}, {@link #remove}) laufen genau einmal.
 *
 * <p>Die Regel kommt je Aufruf aus dem {@link Supplier} ({@code security.keepass.retryCount},
 * {@code security.keepass.retryDelayMillis}) und gilt damit ohne Neustart. Hält selbst kein Secret.
 */
public final class RetryingSecretProvider implements SecretProvider, SecretVault {

    private static final Logger LOG = Logger.getLogger(RetryingSecretProvider.class.getName());

    private final SecretProvider delegate;
    private final Supplier<RetryPolicy> policy;
    private final RetryPolicy.Sleeper sleeper;

    public RetryingSecretProvider(SecretProvider delegate, Supplier<RetryPolicy> policy) {
        this(delegate, policy, RetryPolicy.Sleeper.threadSleep());
    }

    RetryingSecretProvider(SecretProvider delegate, Supplier<RetryPolicy> policy, RetryPolicy.Sleeper sleeper) {
        if (delegate == null || policy == null || sleeper == null) {
            throw new IllegalArgumentException("delegate, policy and sleeper must not be null");
        }
        this.delegate = delegate;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    /** Der umhüllte Provider (Tests, Diagnose). */
    public SecretProvider delegate() {
        return delegate;
    }

    /** Nur „nicht erreichbar“ ist vorübergehend. */
    static boolean isTransient(SecretUnavailableException failure) {
        return failure.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE;
    }

    @Override
    public SecretMaterial resolve(final SecretRef ref) throws SecretUnavailableException {
        return retrying(delegate::resolve, ref);
    }

    @Override
    public List<SecretEntry> entries() throws SecretUnavailableException {
        return retrying(ignored -> delegate.entries(), null);
    }

    @Override
    public String locationOf(SecretRef ref) throws SecretUnavailableException {
        return retrying(delegate::locationOf, ref);
    }

    @Override
    public Map<String, String> attributesOf(SecretRef ref) throws SecretUnavailableException {
        return retrying(delegate::attributesOf, ref);
    }

    @Override
    public void setAttribute(SecretRef ref, String name, String value) throws SecretUnavailableException {
        delegate.setAttribute(ref, name, value);
    }

    @Override
    public List<VaultEntry> list() throws SecretUnavailableException {
        return retrying(ignored -> SecretVaults.of(delegate).list(), null);
    }

    @Override
    public VaultEntryDraft read(final String title) throws SecretUnavailableException {
        return retrying(ignored -> SecretVaults.of(delegate).read(title), null);
    }

    @Override
    public void save(VaultEntryDraft entry) throws SecretUnavailableException {
        SecretVaults.of(delegate).save(entry);
    }

    @Override
    public void create(com.aresstack.enterpriseai.adyton.VaultEntryDraft entry)
            throws SecretUnavailableException {
        SecretVaults.of(delegate).create(entry);
    }

    @Override
    public void remove(String title) throws SecretUnavailableException {
        SecretVaults.of(delegate).remove(title);
    }

    private interface Read<T> {
        T apply(SecretRef ref) throws SecretUnavailableException;
    }

    private <T> T retrying(final Read<T> read, final SecretRef ref) throws SecretUnavailableException {
        RetryPolicy current = policy.get();
        if (current == null) {
            current = RetryPolicy.none();
        }
        return current.call(() -> read.apply(ref), SecretUnavailableException.class,
                RetryingSecretProvider::isTransient,
                (retry, retries, delay, failure) -> LOG.info("KeePass nicht erreichbar"
                        + (ref == null ? "" : " (" + ref + ")") + "; Wiederholung " + retry + " von " + retries
                        + " in " + delay + " ms"),
                sleeper);
    }

    @Override
    public String toString() {
        return "RetryingSecretProvider[" + delegate + ", " + policy.get() + "]";
    }
}
