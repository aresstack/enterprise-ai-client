/**
 * Produktiver {@link com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort} (Strang D, AP9):
 * {@link com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex} kombiniert einen persistenten
 * Lucene-BM25-Index mit einem persistenten Vektorindex für exakte Cosine-Suche, beide je Embedding-Namespace.
 * Lucene-Typen verlassen dieses Modul nicht.
 */
package com.aresstack.enterpriseai.knowledge.lucene;
