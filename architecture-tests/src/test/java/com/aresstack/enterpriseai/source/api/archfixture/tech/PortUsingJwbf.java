package com.aresstack.enterpriseai.source.api.archfixture.tech;

import net.sourceforge.jwbf.archstub.JwbfStub;

/** Absichtlicher Verstoß: source-api kennt JWBF. */
public final class PortUsingJwbf {

    private final JwbfStub jwbf = new JwbfStub();

    public String describe() {
        return jwbf.describe();
    }
}
