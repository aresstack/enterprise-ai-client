/**
 * Indexierung und Dokumentzugriff (AP10, AP20): {@link com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase}
 * bringt Ressourcen einer Wissensquelle über Discovery, Laden, Chunken und Embedden in den Wissensindex;
 * {@link com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase} lädt den vollständigen Text
 * einer Ressource aus ihrer Quelle. Die konfigurierten Quellen stehen im
 * {@link com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog}. Spricht nur über
 * {@code KnowledgeSourcePort}, {@code EmbeddingPort} und {@code KnowledgeIndexPort}; kennt keinen Adapter.
 */
package com.aresstack.enterpriseai.application.knowledge;
