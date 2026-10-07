package com.aresstack.enterpriseai.source.confluence.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

import java.util.Arrays;

/** Erlaubt: ein Adapter liest Secret-Material nur innerhalb eines Aufrufs. */
public final class AdapterUsingSecretBriefly {

    public int authenticate(FakeSecretMaterial material) {
        char[] secret = material.copySecret();
        try {
            return secret.length;
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
