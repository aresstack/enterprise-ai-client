package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * Slice D, Teil KeePass, gegen ein laufendes KeePass mit KeePassRPC-Plugin. Parameter:
 * {@code -Dlive.keepass.entry=Titel} (optional {@code -Dlive.keepass.port=12546}); das Einmal-Passwort des Pairings
 * aus KeePass in {@code ENTERPRISE_AI_LIVE_KEEPASS_PAIRING} (Pairing-Schlüssel nur im Speicher: jeder Lauf pairt neu).
 */
public class LiveKeePassIT {

    static KeePassRpcSecretProvider provider() {
        int port = LiveSettings.integer("live.keepass.port", KeePassRpcConfig.DEFAULT_PORT);
        final char[] pairing = LiveSettings.secret(LiveSettings.KEEPASS_PAIRING_ENV);
        return new KeePassRpcSecretProvider(KeePassRpcConfig.builder().port(port).build(),
                new InMemoryPairingKeyStore(), clientDisplayName -> pairing.clone());
    }

    @Test
    public void entryIsResolvedWithoutPrintingIt() throws Exception {
        String entry = LiveSettings.required("live.keepass.entry");
        try (SecretMaterial material = provider().resolve(SecretRef.of("keepass:" + entry))) {
            char[] secret = material.copySecret();
            try {
                assertTrue("leeres Secret", secret.length > 0);
            } finally {
                Arrays.fill(secret, '\0');
            }
            System.out.println("[live] keepass: Eintrag aufgelöst, Benutzer vorhanden: " + material.hasPrincipal());
        }
    }
}
