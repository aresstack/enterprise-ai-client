package com.aresstack.enterpriseai.mcp.solon;

import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.testkit.McpServerRegistryContractTest;

import java.time.Duration;

/**
 * Der Solon-Transport erfüllt denselben Port-Vertrag wie die InProcess-Referenz – über echtes Streamable HTTP
 * auf 127.0.0.1 mit dem echten Solon-MCP-Client.
 */
public class SolonMcpServerRuntimeContractTest extends McpServerRegistryContractTest {

    @Override
    protected McpServerRegistry createRegistry() {
        return new SolonMcpServerRuntime();
    }

    @Override
    protected McpToolClientFactory clientFactory(McpServerRegistry registry) {
        return new SolonMcpToolClientFactory(Duration.ofSeconds(10), Duration.ofSeconds(10));
    }
}
