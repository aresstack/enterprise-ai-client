/**
 * Wissens-Fachobjekte (Strang D, AP7): providerneutrale Beschreibung von Ressourcen, ihr geladener Inhalt und
 * die daraus abgeleiteten Chunks. Keine Lucene-, HTTP-, Wiki- oder Confluence-Typen.
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId} – konfigurierte Quelle</li>
 *   <li>{@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId} – stabile Ressourcen-ID
 *       ({@code <schema>:<id>}), Schlüssel für Upsert/Replace/Delete im Index</li>
 *   <li>{@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource} – Beschreibung ohne Inhalt
 *       (Titel, Content-Type, {@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision Revision},
 *       Parent/Scope, Ort, {@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeMetadata Metadaten});
 *       liefert ein Source-Port bei {@code discover}</li>
 *   <li>{@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument} – Ressource plus extrahierter,
 *       normalisierter Klartext mit leichtgewichtigem Markdown; liefert ein Source-Port bei {@code load}. Dieser
 *       kanonische Inhalt ist die Wahrheit; jeder Index ist nur eine daraus wiederherstellbare Projektion.</li>
 *   <li>{@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk} – deterministisch erzeugter Abschnitt,
 *       gebildet vom {@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker} nach einer
 *       {@link com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy}</li>
 * </ul>
 *
 * <p>Keine dieser Klassen trägt Zugangsdaten. Eigentümer dieses Unterpakets ist Strang D; andere Stränge ändern
 * es nur über einen abgestimmten Contract-Commit (siehe ARCHITECTURE.md).
 */
package com.aresstack.enterpriseai.domain.knowledge;
