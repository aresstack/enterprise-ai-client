package com.aresstack.enterpriseai.domain.archfixture.tech;

import org.noear.solon.ai.mcp.archstub.SolonMcpStub;

/** Absichtlicher Verstoß: domain kennt Solon MCP. */
public final class SolonMcpInDomain {

    private final SolonMcpStub solonMcp = new SolonMcpStub();

    public String describe() {
        return solonMcp.describe();
    }
}
