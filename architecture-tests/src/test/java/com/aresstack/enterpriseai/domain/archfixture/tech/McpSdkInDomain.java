package com.aresstack.enterpriseai.domain.archfixture.tech;

import io.modelcontextprotocol.archstub.McpSdkStub;

/** Absichtlicher Verstoß: domain kennt das MCP SDK. */
public final class McpSdkInDomain {

    private final McpSdkStub mcpSdk = new McpSdkStub();

    public String describe() {
        return mcpSdk.describe();
    }
}
