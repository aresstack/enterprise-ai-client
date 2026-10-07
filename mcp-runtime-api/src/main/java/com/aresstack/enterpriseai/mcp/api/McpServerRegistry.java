package com.aresstack.enterpriseai.mcp.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Generischer MCP-Server-Port. Endpoints werden mit einem zufälligen, nicht erratbaren Token registriert,
 * ihre Tool-Menge kann zur Laufzeit ersetzt werden (ein Transport meldet das als {@code tools/list_changed})
 * und beim Abmelden wird der Token ungültig. Der Port kennt keine Solon-/MCP-SDK-Typen; der Solon-Transport
 * ({@code mcp-solon-runtime}) und die InProcess-Referenz (Test-Fixtures dieses Moduls) implementieren ihn.
 *
 * <p>Alle Methoden mit {@link McpEndpointHandle} prüfen Endpoint-ID <em>und</em> Token. Ein unbekannter,
 * abgemeldeter oder gefälschter Handle wird still ignoriert bzw. liefert {@code null}/leer; er löst keine
 * Exception aus, die Token-Material tragen könnte.
 *
 * <p>Herkunft: askai-java8 {@code McpServerRegistry}; ergänzt um {@link #shutdown()}.
 */
public interface McpServerRegistry {

    /**
     * Registriert einen Endpoint und erzeugt dafür einen neuen Token. Eine erneute Registrierung derselben
     * Endpoint-ID ersetzt die alte; deren Token ist danach ungültig.
     *
     * @throws IllegalStateException nach {@link #shutdown()}
     */
    McpEndpointHandle registerEndpoint(McpEndpointDefinition definition);

    /** Ersetzt die komplette Tool-Menge des Endpoints ({@code null} = leer). Reihenfolge bleibt erhalten. */
    void updateTools(McpEndpointHandle handle, Collection<McpToolContribution> tools);

    /** Meldet den Endpoint ab und invalidiert seinen Token. Idempotent. */
    void unregisterEndpoint(McpEndpointHandle handle);

    /**
     * Client-URL des Endpoints (Token im Pfad) oder {@code null}, wenn der Handle unbekannt/abgemeldet ist.
     * Die URL ist Secret-Material: nicht loggen.
     */
    String endpointUrl(McpEndpointHandle handle);

    /** Die aktuellen Tool-Namen des Endpoints (live, keine Client-Kopie); leer bei ungültigem Handle. */
    List<String> toolNames(McpEndpointHandle handle);

    /**
     * Die aktuellen Tools als Name → Beschreibung, in Registrierungsreihenfolge; leer bei ungültigem Handle.
     * Damit kann eine Oberfläche den Katalog anzeigen, ohne Tool-Namen fest zu kodieren.
     */
    Map<String, String> toolCatalog(McpEndpointHandle handle);

    /**
     * Meldet alle Endpoints ab (alle Tokens ungültig). Idempotent; danach schlägt
     * {@link #registerEndpoint(McpEndpointDefinition)} fehl.
     */
    void shutdown();
}
