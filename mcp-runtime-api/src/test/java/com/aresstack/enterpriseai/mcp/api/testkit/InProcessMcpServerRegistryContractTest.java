package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;

/** Die InProcess-Referenz erfüllt den Port-Vertrag. */
public class InProcessMcpServerRegistryContractTest extends McpServerRegistryContractTest {

    @Override
    protected McpServerRegistry createRegistry() {
        return new InProcessMcpServerRegistry();
    }

    @Override
    protected McpToolClientFactory clientFactory(McpServerRegistry registry) {
        return new InProcessMcpToolClientFactory((InProcessMcpServerRegistry) registry);
    }
}
