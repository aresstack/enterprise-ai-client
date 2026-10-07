package com.aresstack.enterpriseai.acp.solon.archfixture.signature;

import reactor.core.archstub.ReactorStub;

/** Absichtlicher Verstoß: öffentlicher Parameter mit Reactor-Typ. */
public final class ConnectorExposingReactor {

    public void subscribe(ReactorStub publisher) {
    }
}
