package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.secret;

import com.aresstack.enterpriseai.adyton.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: ein Index speichert Secret-Material. */
public final class IndexStoringSecret {

    private final FakeSecretMaterial credentials;

    public IndexStoringSecret(FakeSecretMaterial credentials) {
        this.credentials = credentials;
    }

    public boolean hasCredentials() {
        return credentials != null;
    }
}
