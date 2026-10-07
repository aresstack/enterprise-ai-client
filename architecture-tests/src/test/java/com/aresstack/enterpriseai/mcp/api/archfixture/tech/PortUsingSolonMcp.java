package com.aresstack.enterpriseai.mcp.api.archfixture.tech;

import org.noear.solon.ai.mcp.archstub.SolonMcpStub;

/** Absichtlicher Verstoß (Nachtrag 5): mcp-runtime-api kennt Solon MCP. */
public final class PortUsingSolonMcp {

    private final SolonMcpStub solonMcp = new SolonMcpStub();

    public String describe() {
        return solonMcp.describe();
    }
}
