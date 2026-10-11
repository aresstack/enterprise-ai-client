/**
 * MCP-Wissenswerkzeuge: {@link com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools} liefert genau zwei
 * kleine Werkzeuge im askai-Stil (Beispiele in der Beschreibung, Fehler mit Beispiel):
 * {@code knowledge_search(query, limit)} und {@code knowledge_read(id)}. Welche Quellen sie sehen, entscheidet der
 * Katalog, den die Composition Root übergibt, nicht das Modell.
 *
 * <p>Suche füllt den Index (MainframeMate): {@code knowledge_search} durchsucht die Quellen live und den Index
 * hybrid; die Live-Treffer lädt der Vorlader ({@code KnowledgePrefetchService}) im Hintergrund und indexiert sie nur
 * bei geänderter Revision bzw. geändertem Inhalts-Hash. {@code knowledge_read} liest über den Priority-Lader (Cache,
 * sonst sofort) und nimmt das Dokument bei Bedarf in den Index auf. Ein Seitenbudget verbrauchen die Werkzeuge nicht.
 *
 * <p>Die Handler rufen nur Application-Use-Cases; Port-Meldungen gelangen nie in eine Antwort.
 */
package com.aresstack.enterpriseai.astu.acropolis.mcp;
