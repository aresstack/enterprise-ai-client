package com.aresstack.enterpriseai.astu.acropolis.archfixture.secret;

import com.aresstack.enterpriseai.adyton.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: ein Use Case nimmt Secret-Material entgegen. */
public final class UseCaseTouchingSecret {

    public int execute(FakeSecretMaterial material) {
        return material.copySecret().length;
    }
}
