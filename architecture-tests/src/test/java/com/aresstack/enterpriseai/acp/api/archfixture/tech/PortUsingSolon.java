package com.aresstack.enterpriseai.acp.api.archfixture.tech;

import org.noear.solon.archstub.SolonStub;

/** Absichtlicher Verstoß (Nachtrag 4): acp-client-api kennt Solon. */
public final class PortUsingSolon {

    private final SolonStub solon = new SolonStub();

    public String describe() {
        return solon.describe();
    }
}
