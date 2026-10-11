package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.exedra.security.DialogCredentialPrompt;
import com.aresstack.enterpriseai.proasteion.exedra.security.SwingPairingCallback;
import com.aresstack.enterpriseai.proasteion.exedra.settings.ConnectionChecker;
import com.aresstack.enterpriseai.proasteion.exedra.settings.ConnectionProbe;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Der Verbindungstest des Einstellungen-Dialogs: holt den API-Key wie die Anwendung über den Tresorzugang des
 * Kompositionspunkts ({@link AdapterAssembly#keePassAccess}: Pairing-Dialog, Pairing-Schlüssel in der
 * konfigurierten Datei) und lässt dann {@link ConnectionProbe} den Weg zum Dienst Schritt für Schritt abgehen.
 * Kann KeePass den Key nicht liefern, nennt die Meldung nur den Grund; der Test läuft ohne Key weiter.
 */
public final class ServiceConnectionChecker implements ConnectionChecker {

    private final KeePassSecretChecker.PairingCallbackFactory pairing;

    /** Produktiv: der Swing-Pairing-Dialog. */
    public ServiceConnectionChecker() {
        this(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public PairingPrompt forAddress(String keePassAddress) {
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
     * Der Embedding-Test: der API-Key des Embedding-Dienstes kommt wie bei der Anwendung aus KeePass (ohne eigenen
     * Eintrag der des Chats), dann ein Aufruf über den Adapter des Kompositionspunkts.
     */
    @Override
    public int embeddingDimension(AppConfig config) throws Exception {
        if (config == null || config.embedding() == null) {
            throw new IllegalArgumentException("config with embedding section required");
        }
        SecretRef ref = config.embedding().apiKeyRef() == null ? config.chat().apiKeyRef() : config.embedding().apiKeyRef();
        String token = null;
        if (config.keePass().enabled() && ref != null) {
            try {
                token = AdapterAssembly.keePassAccess(config.keePass(), pairing.forAddress(config.keePass().address()),
                        new DialogCredentialPrompt()).token(ref);
            } catch (KeePassAccess.Failure e) {
                throw new IOException(e.getMessage());
            }
        }
        return AdapterAssembly.embeddingDimension(config.embedding(), token, NetworkServices.from(config.network()));
    }

    /**
     * Der API-Key des Chats über KeePass, wie die Anwendung ihn holt (Pairing-Dialog bei Bedarf); {@code null} ohne
     * KeePass oder Referenz. Auch für die Modellabfrage des Einstellungen-Dialogs ({@link DialogModelCatalogs}).
     */
    static String chatToken(AppConfig config, KeePassSecretChecker.PairingCallbackFactory pairing)
            throws IOException {
        SecretRef ref = config.chat().apiKeyRef();
        if (!config.keePass().enabled() || ref == null) {
            return null;
        }
        try {
            return AdapterAssembly.keePassAccess(config.keePass(), pairing.forAddress(config.keePass().address()),
                    new DialogCredentialPrompt()).token(ref);
        } catch (KeePassAccess.Failure e) {
            // Nur der Grund wandert in die Anzeige; die Ausnahme selbst bleibt hier.
            throw new IOException(e.getMessage());
        }
    }
}
