package com.aresstack.enterpriseai.security.api.archfixture.secret;

/** Absichtlicher Verstoß: auch im Port-Modul selbst darf kein Cache Secret-Material halten. */
public final class PortCachingSecret {

    private FakeSecretMaterial last;

    public void remember(FakeSecretMaterial material) {
        this.last = material;
    }

    public boolean hasLast() {
        return last != null;
    }
}
