package com.aresstack.enterpriseai.domain.archfixture.tech;

import reactor.core.archstub.ReactorStub;

/** Absichtlicher Verstoß: domain kennt Reactor. */
public final class ReactorInDomain {

    private final ReactorStub reactor = new ReactorStub();

    public String describe() {
        return reactor.describe();
    }
}
