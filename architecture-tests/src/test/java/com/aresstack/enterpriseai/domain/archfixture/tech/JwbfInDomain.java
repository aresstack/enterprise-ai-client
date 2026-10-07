package com.aresstack.enterpriseai.domain.archfixture.tech;

import net.sourceforge.jwbf.archstub.JwbfStub;

/** Absichtlicher Verstoß: domain kennt JWBF. */
public final class JwbfInDomain {

    private final JwbfStub jwbf = new JwbfStub();

    public String describe() {
        return jwbf.describe();
    }
}
