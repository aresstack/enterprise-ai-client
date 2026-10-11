package com.aresstack.enterpriseai.astu.archfixture.tech;

import io.modelcontextprotocol.archstub.McpSdkStub;

/** Absichtlicher Verstoß: astu kennt das MCP SDK. */
public final class McpSdkInDomain {

    private final McpSdkStub mcpSdk = new McpSdkStub();

    public String describe() {
        return mcpSdk.describe();
    }
}
