/**
 * RAG-Orchestrierung (AP10): hybrides Retrieval mit Reciprocal Rank Fusion
 * ({@link com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase}), Kontextaufbau mit Token-Budget
 * ({@link com.aresstack.enterpriseai.astu.acropolis.rag.PromptContextAssembler}) und Chat mit Wissen
 * ({@link com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase}). Spricht nur über
 * {@code KnowledgeIndexPort}, {@code EmbeddingPort} und den {@code ChatService}; kennt keinen Adapter.
 */
package com.aresstack.enterpriseai.astu.acropolis.rag;
