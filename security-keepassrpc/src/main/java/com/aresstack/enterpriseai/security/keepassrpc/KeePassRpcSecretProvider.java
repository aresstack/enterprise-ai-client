package com.aresstack.enterpriseai.security.keepassrpc;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException.Reason;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * {@link SecretProvider} auf Basis von KeePassRPC (KeePass 2.x mit KeePassRPC-Plugin).
 *
 * <p>Ein {@link SecretRef} identifiziert einen KeePass-Eintrag über seinen Titel: {@code keepass:<Titel>} oder
 * einfach {@code <Titel>}. Geliefert werden Benutzername (Principal) und Passwort.
 *
 * <p>Ablauf je Auflösung: gespeicherten Pairing-Schlüssel laden; fehlt er, Pairing über den
 * {@link KeePassPairingCallback}; verbinden und per Key-Challenge-Response anmelden; lehnt KeePass den Schlüssel
 * ab (Pairing in KeePass widerrufen), Schlüssel verwerfen und genau einmal neu pairen; Eintrag lesen; Verbindung
 * schließen. Jede Auflösung nutzt eine eigene, kurze Verbindung (wie MainframeMate {@code KeePassProvider}).
 *
 * <p>Fehlerabbildung: KeePass nicht erreichbar → {@link Reason#NOT_AVAILABLE}; kein Eintrag mit dem Titel →
 * {@link Reason#NOT_FOUND}; Pairing-Passwort falsch oder Schlüssel nach neuem Pairing weiter abgelehnt →
 * {@link Reason#ACCESS_DENIED}; Pairing abgebrochen → {@link Reason#CANCELLED}.
 *
 * <p>Herkunft: Architektur nach corenth {@code KeePassRpcSecretMaterialProvider} (schmale Lookup-Naht hinter dem
 * Provider), Funktion nach MainframeMate {@code KeePassProvider}/{@code KeePassRpcClient}; die unvollständige
 * Reflection-Bindung aus corenth wurde nicht übernommen.
 */
public final class KeePassRpcSecretProvider implements SecretProvider {

    private static final Logger LOG = Logger.getLogger(KeePassRpcSecretProvider.class.getName());
    private static final String SCHEME = "keepass:";

    private final KeePassRpcTransport transport;
    private final KeePassPairingKeyStore keyStore;
    private final KeePassPairingCallback pairingCallback;

    /**
     * Produktiver Konstruktor für die Composition Root.
     *
     * @param config          Verbindung zu KeePassRPC
     * @param keyStore        Ablage für den Pairing-Schlüssel
     * @param pairingCallback Benutzerinteraktion für das Pairing ({@link KeePassPairingCallback#unavailable()},
     *                        wenn keine UI vorhanden ist)
     */
    public KeePassRpcSecretProvider(KeePassRpcConfig config, KeePassPairingKeyStore keyStore,
                                    KeePassPairingCallback pairingCallback) {
        this(new WebSocketKeePassRpcTransport(requireNonNull(config, "config")), keyStore, pairingCallback);
    }

    KeePassRpcSecretProvider(KeePassRpcTransport transport, KeePassPairingKeyStore keyStore,
                             KeePassPairingCallback pairingCallback) {
        this.transport = requireNonNull(transport, "transport");
        this.keyStore = requireNonNull(keyStore, "keyStore");
        this.pairingCallback = requireNonNull(pairingCallback, "pairingCallback");
    }

    @Override
    public synchronized SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        if (ref == null) {
            throw new IllegalArgumentException("SecretRef darf nicht null sein");
        }
        String title = entryTitleOf(ref);
        char[] key = keyStore.load();
        try {
            if (key == null || key.length == 0) {
                key = pair(ref);
            }
            try {
                return lookup(ref, title, key);
            } catch (KeePassRpcException e) {
                if (e.kind() != KeePassRpcException.Kind.AUTH_FAILED) {
                    throw unavailable(ref, e);
                }
                LOG.info("KeePassRPC hat das gespeicherte Pairing abgelehnt; neues Pairing für " + ref);
                keyStore.clear();
                wipe(key);
                key = pair(ref);
                try {
                    return lookup(ref, title, key);
                } catch (KeePassRpcException retry) {
                    throw retry.kind() == KeePassRpcException.Kind.AUTH_FAILED
                            ? new SecretUnavailableException(Reason.ACCESS_DENIED, ref,
                                    "KeePassRPC lehnt auch das neue Pairing ab", retry)
                            : unavailable(ref, retry);
                }
            }
        } finally {
            wipe(key);
        }
    }

    /** Titel des KeePass-Eintrags zu einer Referenz ({@code keepass:}-Präfix optional). */
    static String entryTitleOf(SecretRef ref) {
        String id = ref.id();
        if (id.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            id = id.substring(SCHEME.length()).trim();
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException(ref + " nennt keinen KeePass-Eintrag");
        }
        return id;
    }

    private char[] pair(SecretRef ref) throws SecretUnavailableException {
        char[] key;
        try {
            key = transport.pair(pairingCallback);
        } catch (KeePassRpcException e) {
            if (e.kind() == KeePassRpcException.Kind.AUTH_FAILED) {
                throw new SecretUnavailableException(Reason.ACCESS_DENIED, ref,
                        "KeePassRPC-Pairing abgelehnt (Einmal-Passwort falsch?)", e);
            }
            throw unavailable(ref, e);
        }
        if (key == null || key.length == 0) {
            throw new SecretUnavailableException(Reason.CANCELLED, ref, "KeePassRPC-Pairing abgebrochen");
        }
        keyStore.save(key);
        LOG.info("KeePassRPC-Pairing erfolgreich");
        return key;
    }

    private SecretMaterial lookup(SecretRef ref, String title, char[] key)
            throws KeePassRpcException, SecretUnavailableException {
        try (KeePassRpcTransport.Session session = transport.open(key)) {
            KeePassEntry entry = session.findEntryByTitle(title);
            if (entry == null) {
                throw new SecretUnavailableException(Reason.NOT_FOUND, ref, "kein KeePass-Eintrag mit diesem Titel");
            }
            try {
                return new SecretMaterial(ref, entry.userName(), entry.password());
            } finally {
                entry.close();
            }
        }
    }

    private static SecretUnavailableException unavailable(SecretRef ref, KeePassRpcException e) {
        return new SecretUnavailableException(Reason.NOT_AVAILABLE, ref,
                "KeePassRPC nicht verfügbar: " + e.getMessage(), e);
    }

    private static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " darf nicht null sein");
        }
        return value;
    }

    @Override
    public String toString() {
        return "KeePassRpcSecretProvider[" + transport + "]";
    }
}
