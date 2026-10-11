package com.aresstack.enterpriseai.astu.archfixture.tech;

import net.sourceforge.jwbf.archstub.JwbfStub;

/** Absichtlicher Verstoß: astu kennt JWBF. */
public final class JwbfInDomain {

    private final JwbfStub jwbf = new JwbfStub();

    public String describe() {
        return jwbf.describe();
    }
}
