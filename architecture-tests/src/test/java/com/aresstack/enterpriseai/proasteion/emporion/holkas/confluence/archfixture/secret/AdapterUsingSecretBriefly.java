package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.archfixture.secret;

import com.aresstack.enterpriseai.adyton.archfixture.secret.FakeSecretMaterial;

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
