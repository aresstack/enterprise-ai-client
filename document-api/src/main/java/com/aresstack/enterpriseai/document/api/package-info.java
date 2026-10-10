/**
 * Neutraler Port für Dokument-Erkennung und Text-Extraktion (übernommen aus corenth {@code deigma}, dort
 * aus MainframeMate {@code de.bund.zrb.ingestion} abgeleitet): {@link com.aresstack.enterpriseai.document.api.ContentDetector},
 * {@link com.aresstack.enterpriseai.document.api.ResourceExtractor} und das Dokumentmodell. Kein Tika, keine
 * Fremdbibliothek; die Umsetzungen liegen im Adapter {@code document-tika}.
 */
package com.aresstack.enterpriseai.document.api;
