/**
 * Test-Fixtures des MCP-Runtime-Ports (kein Produktionscode): transportfreie InProcess-Referenz
 * ({@link com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpServerRegistry} plus
 * {@link com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpToolClientFactory}), die neutralen Test-Tools
 * {@link com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools} und die abstrakten Vertragstests
 * {@link com.aresstack.enterpriseai.mcp.api.testkit.McpServerRegistryContractTest}, die jede Implementierung
 * des Ports erfüllen muss.
 *
 * <p>Verwendung in anderen Modulen: {@code testImplementation testFixtures(project(':mcp-runtime-api'))}.
 */
package com.aresstack.enterpriseai.mcp.api.testkit;
