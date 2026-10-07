package com.aresstack.enterpriseai.security.api.archfixture.secret;

/** Stellvertreter für {@code SecretMaterial} in den Selbsttests von {@code SecretBoundaryRules}. */
public final class FakeSecretMaterial {

    public char[] copySecret() {
        return new char[0];
    }
}
