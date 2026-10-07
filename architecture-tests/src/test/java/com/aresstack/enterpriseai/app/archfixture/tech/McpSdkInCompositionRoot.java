package com.aresstack.enterpriseai.app.archfixture.tech;

import io.modelcontextprotocol.archstub.McpSdkStub;

/** Absichtlicher Verstoß: die Composition Root verdrahtet Adapter, spricht das MCP SDK aber nicht selbst an. */
public final class McpSdkInCompositionRoot {

    private final McpSdkStub mcpSdk = new McpSdkStub();

    public String describe() {
        return mcpSdk.describe();
    }
}
