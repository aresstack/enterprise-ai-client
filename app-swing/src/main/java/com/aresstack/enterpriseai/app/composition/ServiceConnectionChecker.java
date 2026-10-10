package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SecretAccessException;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.app.settings.ConnectionChecker;
import com.aresstack.enterpriseai.app.settings.ConnectionProbe;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Der Verbindungstest des Einstellungen-Dialogs: holt den API-Key wie die Anwendung über denselben
 * {@code KeePassRpcSecretProvider} (Pairing-Dialog, Pairing-Schlüssel in der konfigurierten Datei) und dieselbe
 * {@link SecretBackedTokenSource} und lässt dann {@link ConnectionProbe} den Weg zum Dienst Schritt für Schritt
 * abgehen. Kann KeePass den Key nicht liefern, nennt die Meldung nur den Grund; der Test läuft ohne Key weiter.
 * Liegt in {@code app.composition}, weil nur dort Adapter entstehen (AP23).
 */
public final class ServiceConnectionChecker implements ConnectionChecker {

    private final KeePassSecretChecker.PairingCallbackFactory pairing;

    /** Produktiv: der Swing-Pairing-Dialog. */
    public ServiceConnectionChecker() {
        this(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public KeePassPairingCallback forAddress(String keePassAddress) {
                return new SwingPairingCallback(keePassAddress);
            }
        });
    }

    public ServiceConnectionChecker(KeePassSecretChecker.PairingCallbackFactory pairing) {
        if (pairing == null) {
            throw new IllegalArgumentException("pairing must not be null");
        }
        this.pairing = pairing;
    }

    @Override
    public boolean check(final AppConfig config, Consumer<ConnectionCheckStep> onStep) {
        if (config == null || onStep == null) {
            throw new IllegalArgumentException("config and onStep must not be null");
        }
        ConnectionProbe.TokenLookup tokens = new ConnectionProbe.TokenLookup() {
            @Override
            public String token() throws IOException {
                return chatToken(config, pairing);
            }
        };
        return new ConnectionProbe(config, tokens).run(onStep);
    }

    /**
     * Der API-Key des Chats über KeePass, wie die Anwendung ihn holt (Pairing-Dialog bei Bedarf); {@code null} ohne
     * KeePass oder Referenz. Auch für die Modellabfrage des Einstellungen-Dialogs ({@link DialogModelCatalogs}).
     */
    static String chatToken(AppConfig config, KeePassSecretChecker.PairingCallbackFactory pairing)
            throws IOException {
        KeePassConfig keePass = config.keePass();
        SecretRef ref = config.chat().apiKeyRef();
        if (!keePass.enabled() || ref == null) {
            return null;
        }
        String address = keePass.rpc().host() + ":" + keePass.rpc().port();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        SecretProvider provider = AdapterAssembly.secrets(keePass, pairing.forAddress(address), keyStore);
        try {
            return new SecretBackedTokenSource(provider, ref).token();
        } catch (SecretAccessException e) {
            // Nur der Grund wandert in die Anzeige; die Ausnahme selbst bleibt hier.
            throw new IOException(KeePassSecretChecker.describe(e.reason(), e.ref(), address));
        }
    }
}
