package com.aresstack.enterpriseai.source.confluence.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: ein erlaubter Adapter hält Secret-Material über den Aufruf hinaus. */
public final class AdapterStoringSecret {

    private FakeSecretMaterial cachedLogin;

    public void remember(FakeSecretMaterial material) {
        this.cachedLogin = material;
    }

    public boolean loggedIn() {
        return cachedLogin != null;
    }
}
