package com.aresstack.enterpriseai.proasteion.katagogion.archfixture.tech;

import org.noear.solon.ai.mcp.archstub.SolonMcpStub;

/** Absichtlicher Verstoß (Nachtrag 5): katagogion kennt Solon MCP. */
public final class PortUsingSolonMcp {

    private final SolonMcpStub solonMcp = new SolonMcpStub();

    public String describe() {
        return solonMcp.describe();
    }
}
