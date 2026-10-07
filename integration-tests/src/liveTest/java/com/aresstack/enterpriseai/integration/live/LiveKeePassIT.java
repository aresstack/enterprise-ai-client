package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Stufe 6 der Live-Verifikation: Slice D, Teil KeePass, gegen ein laufendes, entsperrtes KeePass 2.x mit
 * KeePassRPC-Plugin. Parameter: {@code -Dlive.keepass.entry=Titel} (optional {@code -Dlive.keepass.port=12546},
 * {@code -Dlive.keepass.host=127.0.0.1}, {@code -Dlive.keepass.origin=…}, {@code -Dlive.keepass.clientId=…},
 * {@code -Dlive.keepass.pairingKeyFile=…}).
 *
 * <p>Pairing: KeePass zeigt das Einmal-Passwort erst an, wenn sich der Client meldet; der Test fragt es deshalb
 * mit dem Pairing-Dialog der Anwendung ab ({@code SwingPairingCallback}, braucht ein Display) und legt den
 * Pairing-Schlüssel wie die Anwendung in einer Datei ab (Standard {@code build/live/keepassrpc-pairing.key},
 * Rechte nur für den Besitzer), damit Stufe 7 ohne neues Pairing läuft. Ist die Umgebungsvariable
 * {@code ENTERPRISE_AI_LIVE_KEEPASS_PAIRING} gesetzt, wird ihr Wert statt des Dialogs verwendet.
 */
public class LiveKeePassIT {

    private static final int STAGE = 6;
    private static final String DEFAULT_PAIRING_KEY_FILE = "build/live/keepassrpc-pairing.key";

    static Path pairingKeyFile() {
        String configured = LiveSettings.optional("live.keepass.pairingKeyFile");
        return Paths.get(configured == null ? DEFAULT_PAIRING_KEY_FILE : configured).toAbsolutePath();
    }

    static KeePassRpcConfig config() {
        KeePassRpcConfig.Builder config = KeePassRpcConfig.builder()
                .port(LiveSettings.integer("live.keepass.port", KeePassRpcConfig.DEFAULT_PORT))
                .clientDisplayName("Enterprise AI Client (Live-Verifikation)");
        if (LiveSettings.optional("live.keepass.host") != null) {
            config.host(LiveSettings.optional("live.keepass.host"));
        }
        if (LiveSettings.optional("live.keepass.origin") != null) {
            config.origin(LiveSettings.optional("live.keepass.origin"));
        }
        if (LiveSettings.optional("live.keepass.clientId") != null) {
            config.clientId(LiveSettings.optional("live.keepass.clientId"));
        }
        return config.build();
    }

    /** @return ob ein Pairing-Schlüssel vorliegt, ohne ihn länger als nötig im Speicher zu halten */
    static boolean hasPairingKey(KeePassPairingKeyStore store) {
        char[] key = store.load();
        if (key == null) {
            return false;
        }
        Arrays.fill(key, '\0');
        return true;
    }

    /** Dialog der Anwendung, Umgebungsvariable oder nichts; {@code prompts} zählt, wie oft gefragt wurde. */
    static KeePassPairingCallback pairingCallback(KeePassRpcConfig config, final AtomicInteger prompts) {
        final KeePassPairingCallback inner;
        if (LiveSettings.hasSecret(LiveSettings.KEEPASS_PAIRING_ENV)) {
            inner = clientDisplayName -> LiveSettings.secret(LiveSettings.KEEPASS_PAIRING_ENV);
        } else if (!GraphicsEnvironment.isHeadless()) {
            inner = new SwingPairingCallback(config.host() + ":" + config.port());
        } else {
            inner = KeePassPairingCallback.unavailable();
        }
        return clientDisplayName -> {
            prompts.incrementAndGet();
            return inner.requestPairingPassword(clientDisplayName);
        };
    }

    /** Provider wie in Stufe 7 verwendet; überspringt den Test, wenn weder Schlüssel noch Dialog möglich sind. */
    static KeePassRpcSecretProvider provider(AtomicInteger prompts) {
        KeePassRpcConfig config = config();
        KeePassPairingKeyStore store = new FilePairingKeyStore(pairingKeyFile());
        boolean paired = hasPairingKey(store);
        assumeTrue("Live-Test übersprungen: Pairing braucht ein Display für den Dialog (oder -Dlive.headless=false), "
                + "einen vorhandenen Pairing-Schlüssel oder " + LiveSettings.KEEPASS_PAIRING_ENV,
                paired || !GraphicsEnvironment.isHeadless() || LiveSettings.hasSecret(LiveSettings.KEEPASS_PAIRING_ENV));
        return new KeePassRpcSecretProvider(config, store, pairingCallback(config, prompts));
    }

    @Test
    public void entryIsResolvedWithoutPrintingIt() throws Exception {
        final String entry = LiveSettings.required("live.keepass.entry");
        LiveSettings.withoutSecretLeak(() -> {
            boolean pairedBefore = hasPairingKey(new FilePairingKeyStore(pairingKeyFile()));
            AtomicInteger prompts = new AtomicInteger();
            KeePassRpcSecretProvider provider = provider(prompts);

            boolean principal;
            int secretLength;
            try (SecretMaterial material = provider.resolve(SecretRef.of("keepass:" + entry))) {
                char[] secret = material.copySecret();
                try {
                    secretLength = secret.length;
                } finally {
                    Arrays.fill(secret, '\0');
                }
                principal = material.hasPrincipal();
            }
            assertTrue("leeres Passwortfeld", secretLength > 0);
            LiveSettings.report(STAGE, "Pairing: " + (pairedBefore ? "Schlüssel aus Datei wiederverwendet"
                    : (prompts.get() > 0 ? "neu (Einmal-Passwort abgefragt)" : "neu ohne Rückfrage")) + ", Schlüsseldatei "
                    + (hasPairingKey(new FilePairingKeyStore(pairingKeyFile())) ? "vorhanden" : "FEHLT"));
            LiveSettings.report(STAGE, "Eintrag aufgelöst: Benutzername " + (principal ? "vorhanden" : "leer")
                    + ", Passwortfeld " + secretLength + " Zeichen (nicht ausgegeben)");

            int promptsBefore = prompts.get();
            try (SecretMaterial again = provider.resolve(SecretRef.of("keepass:" + entry))) {
                assertEquals("zweite Auflösung liefert anderen Benutzernamen-Status", principal, again.hasPrincipal());
            }
            assertEquals("zweite Auflösung hat erneut gepairt", promptsBefore, prompts.get());
            LiveSettings.report(STAGE, "zweite Auflösung ohne erneutes Pairing: ja");
        }, LiveSettings.KEEPASS_PAIRING_ENV);
    }
}
