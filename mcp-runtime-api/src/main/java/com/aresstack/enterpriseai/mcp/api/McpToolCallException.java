package com.aresstack.enterpriseai.mcp.api;

/**
 * Fehler eines {@link McpToolClient}-Aufrufs. Unterscheidet über die neutrale Grenze hinweg zwischen einem
 * Tool-Fehler (Tool unbekannt, Tool meldet Fehler) und einem nicht erreichbaren Endpoint (Verbindung,
 * Zeitüberschreitung, Endpoint abgemeldet, falscher Token). Die Meldung enthält nie die Endpoint-URL.
 *
 * <p>Herkunft: askai-java8 {@code McpToolClient.McpToolCallException}, hier als eigene Klasse.
 */
public class McpToolCallException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean endpointUnavailable;

    public McpToolCallException(String message, boolean endpointUnavailable) {
        super(message);
        this.endpointUnavailable = endpointUnavailable;
    }

    public McpToolCallException(String message, boolean endpointUnavailable, Throwable cause) {
        super(message, cause);
        this.endpointUnavailable = endpointUnavailable;
    }

    /** @return {@code true}, wenn der Endpoint nicht erreichbar ist; {@code false} bei einem Tool-Fehler. */
    public boolean isEndpointUnavailable() {
        return endpointUnavailable;
    }
}
