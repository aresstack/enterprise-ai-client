package com.aresstack.enterpriseai.acp.api.archfixture.tech;

import reactor.core.archstub.ReactorStub;

/** Absichtlicher Verstoß (Nachtrag 4): acp-client-api kennt Reactor. */
public final class PortUsingReactor {

    private final ReactorStub reactor = new ReactorStub();

    public String describe() {
        return reactor.describe();
    }
}
