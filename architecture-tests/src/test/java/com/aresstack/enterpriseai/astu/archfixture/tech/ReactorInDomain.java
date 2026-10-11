package com.aresstack.enterpriseai.astu.archfixture.tech;

import reactor.core.archstub.ReactorStub;

/** Absichtlicher Verstoß: astu kennt Reactor. */
public final class ReactorInDomain {

    private final ReactorStub reactor = new ReactorStub();

    public String describe() {
        return reactor.describe();
    }
}
