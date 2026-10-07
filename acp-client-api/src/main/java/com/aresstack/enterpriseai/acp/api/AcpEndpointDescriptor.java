package com.aresstack.enterpriseai.acp.api;

/**
 * Neutral description of an MCP endpoint handed to the external agent: url, transport, token, endpointId.
 * The ACP layer knows ONLY this data — never the MCP runtime, its tool policy or its server registry.
 *
 * <p>The token is a short-lived access credential for that endpoint, and the URL path may carry it as well
 * (e.g. {@code /mcp/<id>/<token>}). {@link #toString()} therefore shows neither the token nor the URL path,
 * so a descriptor can be logged or put into an exception message safely.</p>
 */
public final class AcpEndpointDescriptor {

    private final String endpointId;
    private final String url;
    private final String transport;
    private final String token;

    public AcpEndpointDescriptor(String endpointId, String url, String transport, String token) {
        this.endpointId = endpointId;
        this.url = url;
        this.transport = transport;
        this.token = token;
    }

    public String getEndpointId() { return endpointId; }
    public String getUrl() { return url; }
    public String getTransport() { return transport; }
    public String getToken() { return token; }

    /**
     * Never contains the token and never the URL path: the path of a local MCP endpoint carries its token
     * ({@code /mcp/<id>/<token>}), so only scheme, host and port are shown.
     */
    @Override
    public String toString() {
        return "AcpEndpointDescriptor{endpointId=" + endpointId + ", url=" + Redaction.url(url)
                + ", transport=" + transport
                + ", token=" + (token == null || token.isEmpty() ? "<none>" : Redaction.MASK) + "}";
    }
}
