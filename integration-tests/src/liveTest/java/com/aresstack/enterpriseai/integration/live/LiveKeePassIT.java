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
 * Pairing-Schlüssel wie die Anwendung in einer Datei ab (Standard {@code build/live/keepassrpc-pairing-<clientId>.key},
 * Rechte nur für den Besitzer), damit Stufe 7 ohne neues Pairing läuft. Ohne Angabe pairt der Test unter der
 * eigenen Kennung {@value #DEFAULT_LIVE_CLIENT_ID}, weil KeePassRPC je Kennung genau einen Schlüssel kennt und
 * ein Pairing unter der Kennung der Anwendung deren Pairing ersetzen würde. Die Schlüsseldatei trägt die Kennung
 * im Namen, damit ein Schlüssel nie unter einer anderen Kennung als der, mit der er gepairt wurde, verwendet wird.
 */
public class LiveKeePassIT {

    private static final int STAGE = 6;
    static final String DEFAULT_LIVE_CLIENT_ID = "EnterpriseAiClientLive";

    static String clientId() {
        String configured = LiveSettings.optional("live.keepass.clientId");
        return configured == null ? DEFAULT_LIVE_CLIENT_ID : configured;
    }

    /** Standard: {@code build/live/keepassrpc-pairing-<clientId>.key}; {@code -Dlive.keepass.pairingKeyFile} überstimmt. */
    static Path pairingKeyFile() {
        String configured = LiveSettings.optional("live.keepass.pairingKeyFile");
        if (configured != null) {
            return Paths.get(configured).toAbsolutePath();
        }
        String fileSafeClientId = clientId().replaceAll("[^A-Za-z0-9._-]", "_");
        return Paths.get("build/live/keepassrpc-pairing-" + fileSafeClientId + ".key").toAbsolutePath();
    }

    static KeePassRpcConfig config() {
        KeePassRpcConfig.Builder config = KeePassRpcConfig.builder()
                .port(LiveSettings.integer("live.keepass.port", KeePassRpcConfig.DEFAULT_PORT))
                .clientId(clientId())
                .clientDisplayName("Enterprise AI Client (Live-Verifikation)");
        if (LiveSettings.optional("live.keepass.host") != null) {
            config.host(LiveSettings.optional("live.keepass.host"));
        }
        if (LiveSettings.optional("live.keepass.origin") != null) {
            config.origin(LiveSettings.optional("live.keepass.origin"));
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

    /** Dialog der Anwendung (mit Display) oder nichts; {@code prompts} zählt, wie oft gefragt wurde. */
    static KeePassPairingCallback pairingCallback(KeePassRpcConfig config, final AtomicInteger prompts) {
        final KeePassPairingCallback inner = GraphicsEnvironment.isHeadless()
                ? KeePassPairingCallback.unavailable()
                : new SwingPairingCallback(config.host() + ":" + config.port());
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
        assumeTrue("Live-Test übersprungen: Pairing braucht ein Display für den Dialog (nicht mit -Dlive.headless=true) "
                + "oder einen vorhandenen Pairing-Schlüssel (-Dlive.keepass.pairingKeyFile)",
                paired || !GraphicsEnvironment.isHeadless());
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
            String pairing;
            if (pairedBefore) {
                pairing = prompts.get() == 0 ? "Schlüssel aus Datei wiederverwendet"
                        : "gespeicherter Schlüssel von KeePass abgelehnt, neu gepairt (Einmal-Passwort abgefragt)";
            } else {
                pairing = prompts.get() > 0 ? "neu (Einmal-Passwort abgefragt)" : "neu ohne Rückfrage";
            }
            LiveSettings.report(STAGE, "Pairing: " + pairing + ", Schlüsseldatei "
                    + (hasPairingKey(new FilePairingKeyStore(pairingKeyFile())) ? "vorhanden" : "FEHLT"));
            LiveSettings.report(STAGE, "Eintrag aufgelöst: Benutzername " + (principal ? "vorhanden" : "leer")
                    + ", Passwortfeld " + secretLength + " Zeichen (nicht ausgegeben)");

            int promptsBefore = prompts.get();
            try (SecretMaterial again = provider.resolve(SecretRef.of("keepass:" + entry))) {
                assertEquals("zweite Auflösung liefert anderen Benutzernamen-Status", principal, again.hasPrincipal());
            }
            assertEquals("zweite Auflösung hat erneut gepairt", promptsBefore, prompts.get());
            LiveSettings.report(STAGE, "zweite Auflösung ohne erneutes Pairing: ja");
        });
    }
}
