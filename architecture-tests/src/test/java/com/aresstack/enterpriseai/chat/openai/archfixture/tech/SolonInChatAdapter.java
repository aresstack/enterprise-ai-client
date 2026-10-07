package com.aresstack.enterpriseai.chat.openai.archfixture.tech;

import org.noear.solon.archstub.SolonStub;

/** Absichtlicher Verstoß: Solon außerhalb der ACP-/MCP-Adapter. */
public final class SolonInChatAdapter {

    private final SolonStub solon = new SolonStub();

    public String describe() {
        return solon.describe();
    }
}
