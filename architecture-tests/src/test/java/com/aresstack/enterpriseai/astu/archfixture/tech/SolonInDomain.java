package com.aresstack.enterpriseai.astu.archfixture.tech;

import org.noear.solon.archstub.SolonStub;

/** Absichtlicher Verstoß: astu kennt Solon. */
public final class SolonInDomain {

    private final SolonStub solon = new SolonStub();

    public String describe() {
        return solon.describe();
    }
}
