package com.aresstack.enterpriseai.knowledge.lucene.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

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
