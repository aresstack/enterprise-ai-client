package com.aresstack.enterpriseai.mcp.api;

/**
 * Die kleine, flache Menge an Parametertypen. Bewusst minimal, damit auch kleine Modelle Tools zuverlässig
 * aufrufen.
 *
 * <p>Herkunft: askai-java8 {@code McpToolType}.
 */
public enum McpToolType {
    STRING,
    INTEGER,
    BOOLEAN,
    ENUM
}
