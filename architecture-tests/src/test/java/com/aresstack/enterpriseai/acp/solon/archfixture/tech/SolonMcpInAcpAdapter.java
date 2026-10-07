package com.aresstack.enterpriseai.acp.solon.archfixture.tech;

import org.noear.solon.ai.mcp.archstub.SolonMcpStub;

/** Absichtlicher Verstoß: Solon MCP außerhalb von mcp-solon-runtime. */
public final class SolonMcpInAcpAdapter {

    private final SolonMcpStub solonMcp = new SolonMcpStub();

    public String describe() {
        return solonMcp.describe();
    }
}
