package com.aresstack.enterpriseai.proasteion.katagogion.archfixture.tech;

import io.modelcontextprotocol.archstub.McpSdkStub;

/** Absichtlicher Verstoß (Nachtrag 5): katagogion kennt das MCP SDK. */
public final class PortUsingMcpSdk {

    private final McpSdkStub mcpSdk = new McpSdkStub();

    public String describe() {
        return mcpSdk.describe();
    }
}
