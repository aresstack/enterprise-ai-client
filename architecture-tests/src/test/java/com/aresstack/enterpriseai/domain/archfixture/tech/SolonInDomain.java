package com.aresstack.enterpriseai.domain.archfixture.tech;

import org.noear.solon.archstub.SolonStub;

/** Absichtlicher Verstoß: domain kennt Solon. */
public final class SolonInDomain {

    private final SolonStub solon = new SolonStub();

    public String describe() {
        return solon.describe();
    }
}
