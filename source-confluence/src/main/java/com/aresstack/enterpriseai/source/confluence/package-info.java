/**
 * Adapter: Confluence Data Center als Knowledge Source (Strang F, AP15).
 *
 * <p>Einstieg ist {@link com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource}; HTTP läuft über
 * die Naht {@link com.aresstack.enterpriseai.source.confluence.ConfluenceHttpTransport} (produktiv
 * {@link com.aresstack.enterpriseai.source.confluence.UrlConnectionConfluenceTransport}, Proxy und mTLS von außen),
 * Anmeldedaten ausschließlich über {@code security-api}. Fachlich übernommen aus MainframeMate
 * {@code ConfluenceRestClient}.
 */
package com.aresstack.enterpriseai.source.confluence;
