package com.aresstack.enterpriseai.mcp.api;

/**
 * Modul-Anker für {@code mcp-runtime-api}: hält das Modul kompilierbar und für die Architekturtests sichtbar,
 * solange es noch keine fachlichen Klassen enthält. Darf gelöscht werden, sobald das Modul eigene
 * Produktionsklassen in {@code com.aresstack.enterpriseai.mcp.api} besitzt.
 */
public final class McpRuntimeApiModule {

    private McpRuntimeApiModule() {
    }
}
