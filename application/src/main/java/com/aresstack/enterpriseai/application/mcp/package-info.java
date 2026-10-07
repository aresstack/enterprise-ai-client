/**
 * MCP-Wissenswerkzeuge (AP20): {@link com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools} liefert die
 * Tool-Contributions {@code search_knowledge}, {@code get_knowledge_document} und {@code refresh_knowledge_source}
 * für einen MCP-Endpoint. Die Handler rufen ausschließlich Application-Use-Cases auf
 * ({@code RetrieveKnowledgeUseCase}, {@code LoadKnowledgeDocumentUseCase}, {@code IndexKnowledgeUseCase}); sie kennen
 * weder Index-, Embedding- noch Quell-Adapter und keinen Transport. Registriert werden die Contributions in der
 * Composition Root an einem {@code McpServerRegistry}.
 *
 * <p>Ergebnisse sind kurzer, strukturierter Text für das Modell; Fehler sind {@code McpToolResult.error} mit knapper,
 * geheimnisfreier Meldung (keine Stacktraces, keine URLs mit Zugangsdaten, keine Tokens). Die Antwortgröße ist über
 * {@link com.aresstack.enterpriseai.application.mcp.KnowledgeToolSettings} begrenzt; Überschreitungen werden gekürzt
 * und gekennzeichnet.
 *
 * <p>Herkunft: Miguel0888/askai-java8 {@code ResearchBotDirectoryTools} (Tool-Katalog als Fabrik von Contributions,
 * Fehler als Ergebnis statt Exception, Auflösung des Ziels vor dem Aufruf); Miguel0888/MainframeMate
 * {@code SearchIndexTool}/{@code ReadChunksTool} (Parameter query/maxResults/sources, Snippet-Grenze, Gesamtgrenze
 * 20.000 Zeichen).
 */
package com.aresstack.enterpriseai.application.mcp;
