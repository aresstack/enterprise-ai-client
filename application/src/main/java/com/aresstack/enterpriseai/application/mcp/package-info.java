/**
 * MCP-Wissenswerkzeuge: {@link com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools} liefert genau zwei
 * kleine Werkzeuge im askai-Stil (Beispiele in der Beschreibung, Fehler mit Beispiel):
 * {@code knowledge_search(query, limit)} und {@code knowledge_read(id)}. Welche Quellen sie sehen, entscheidet der
 * Katalog, den die Composition Root übergibt, nicht das Modell.
 *
 * <p>Suche füllt den Index (MainframeMate): {@code knowledge_search} durchsucht die Quellen live, lädt höchstens
 * {@link com.aresstack.enterpriseai.application.mcp.KnowledgeToolSettings#maxPages()} Treffer, indexiert sie nur
 * bei geänderter Revision bzw. geändertem Inhalts-Hash und sucht danach hybrid im Index. {@code knowledge_read}
 * lädt ein Dokument aus seiner Quelle und nimmt es bei Bedarf in den Index auf.
 *
 * <p>Die Handler rufen nur Application-Use-Cases; Port-Meldungen gelangen nie in eine Antwort.
 */
package com.aresstack.enterpriseai.application.mcp;
