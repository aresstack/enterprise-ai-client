/**
 * Port of the global metadata catalog (#106): structured facts attached to the stable
 * {@link com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId} of a knowledge resource.
 *
 * <p>{@link com.aresstack.enterpriseai.fact.api.FactStorePort} is the only access path. Fact types are registered at
 * runtime ({@link com.aresstack.enterpriseai.fact.api.FactType}); payloads are JSON objects, so a new type needs
 * neither a Java enum constant nor a new table. The catalog is independent of the knowledge index (no Lucene) and
 * of the bookmark syntax: an optional {@link com.aresstack.enterpriseai.fact.api.FactAnchor} points into the
 * document, the bookmark itself stays the resource id.
 */
package com.aresstack.enterpriseai.fact.api;
