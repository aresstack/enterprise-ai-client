package com.aresstack.enterpriseai.proasteion.katagogion.testkit;

import com.aresstack.enterpriseai.proasteion.katagogion.McpServerRegistry;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClientFactory;

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
