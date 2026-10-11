/**
 * Test-Fixtures des MCP-Runtime-Ports (kein Produktionscode): transportfreie InProcess-Referenz
 * ({@link com.aresstack.enterpriseai.proasteion.katagogion.testkit.InProcessMcpServerRegistry} plus
 * {@link com.aresstack.enterpriseai.proasteion.katagogion.testkit.InProcessMcpToolClientFactory}), die neutralen Test-Tools
 * {@link com.aresstack.enterpriseai.proasteion.katagogion.testkit.McpTestTools} und die abstrakten Vertragstests
 * {@link com.aresstack.enterpriseai.proasteion.katagogion.testkit.McpServerRegistryContractTest}, die jede Implementierung
 * des Ports erfüllen muss.
 *
 * <p>Verwendung in anderen Modulen: {@code testImplementation testFixtures(project(':proasteion:katagogion'))}.
 */
package com.aresstack.enterpriseai.proasteion.katagogion.testkit;
