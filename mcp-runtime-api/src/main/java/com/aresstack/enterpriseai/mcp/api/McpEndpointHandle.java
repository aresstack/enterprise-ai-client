package com.aresstack.enterpriseai.mcp.api;

/**
 * Eine lebende Endpoint-Registrierung: Endpoint-ID plus zufälliger, nicht erratbarer Token, den ein Aufrufer
 * vorweisen muss. Der Token wird beim Abmelden ungültig. {@link #toString()} verrät den Token nie.
 *
 * <p>Herkunft: askai-java8 {@code McpEndpointHandle}; {@code toString()} maskiert jetzt den Token.
 */
public final class McpEndpointHandle {

    private final String endpointId;
    private final String token;

    public McpEndpointHandle(String endpointId, String token) {
        this.endpointId = endpointId;
        this.token = token;
    }

    public String getEndpointId() {
        return endpointId;
    }

    /** Secret-Material: nicht loggen. */
    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "McpEndpointHandle[endpointId=" + endpointId + ", token=***]";
    }
}
