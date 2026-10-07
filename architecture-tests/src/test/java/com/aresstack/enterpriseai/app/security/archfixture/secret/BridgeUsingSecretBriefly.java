package com.aresstack.enterpriseai.app.security.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

import java.util.Arrays;

/** Erlaubt (AP23): Eine Brücke in app.security liest Secret-Material nur innerhalb eines Aufrufs. */
public final class BridgeUsingSecretBriefly {

    public String token(FakeSecretMaterial material) {
        char[] secret = material.copySecret();
        try {
            return new String(secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
