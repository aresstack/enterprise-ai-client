package com.aresstack.enterpriseai.mcp.api;

/**
 * Erzeugt {@link McpToolClient}s. Die URL trägt den Endpoint-Token im Pfad; Implementierungen loggen sie nie
 * und übernehmen sie in keine Exception.
 *
 * <p>Herkunft: askai-java8 {@code McpToolClientFactory}.
 */
public interface McpToolClientFactory {

    /** Standard-Transport des Projekts: MCP Streamable HTTP. */
    String STREAMABLE_HTTP = "streamable";

    /**
     * @param url       Endpoint-URL, z. B. aus {@link McpServerRegistry#endpointUrl(McpEndpointHandle)}
     * @param transport Transportname; {@code null} oder leer bedeutet {@link #STREAMABLE_HTTP}
     */
    McpToolClient connect(String url, String transport);
}
