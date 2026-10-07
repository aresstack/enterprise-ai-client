package com.aresstack.enterpriseai.mcp.solon.archfixture.tech;

import io.modelcontextprotocol.archstub.McpSdkStub;
import org.noear.solon.ai.mcp.archstub.SolonMcpStub;
import org.noear.solon.archstub.SolonStub;

/** Gegenprobe: Solon, Solon MCP und MCP SDK in mcp-solon-runtime sind erlaubt. */
public final class SolonMcpInsideItsRuntime {

    private final SolonStub solon = new SolonStub();
    private final SolonMcpStub solonMcp = new SolonMcpStub();
    private final McpSdkStub mcpSdk = new McpSdkStub();

    public String describe() {
        return solon.describe() + " " + solonMcp.describe() + " " + mcpSdk.describe();
    }
}
