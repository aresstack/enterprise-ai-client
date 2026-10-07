/**
 * Frameworkfreie MCP-Runtime-Verträge (Strang H, AP18). Keine Solon-/MCP-SDK-Typen.
 *
 * <p>Serverseite: {@link com.aresstack.enterpriseai.mcp.api.McpServerRegistry} registriert logische Endpoints
 * mit nicht erratbarem Token, deren Tool-Menge ({@link com.aresstack.enterpriseai.mcp.api.McpToolContribution})
 * zur Laufzeit wechseln darf. Clientseite: {@link com.aresstack.enterpriseai.mcp.api.McpToolClientFactory}
 * verbindet sich mit einer Endpoint-URL und liefert einen
 * {@link com.aresstack.enterpriseai.mcp.api.McpToolClient}.
 *
 * <p>Endpoint-URLs tragen den Token im Pfad und sind deshalb wie ein Secret zu behandeln: nie loggen, nie in
 * Exceptions, {@code toString()}, Chat-Historie oder Indizes übernehmen.
 *
 * <p>Herkunft: Miguel0888/askai-java8, Modul {@code mcp-runtime-api} (Paket {@code com.aresstack.askai.mcp.api}).
 */
package com.aresstack.enterpriseai.mcp.api;
