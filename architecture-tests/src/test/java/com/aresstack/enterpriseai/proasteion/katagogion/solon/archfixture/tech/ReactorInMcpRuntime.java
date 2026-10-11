package com.aresstack.enterpriseai.proasteion.katagogion.solon.archfixture.tech;

import reactor.core.archstub.ReactorStub;

/** Absichtlicher Verstoß: Reactor außerhalb der ACP-Module (solon-ai-mcp zieht es transitiv). */
public final class ReactorInMcpRuntime {

    private final ReactorStub reactor = new ReactorStub();

    public String describe() {
        return reactor.describe();
    }
}
