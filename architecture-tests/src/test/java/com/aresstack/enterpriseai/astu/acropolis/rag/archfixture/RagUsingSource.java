package com.aresstack.enterpriseai.astu.acropolis.rag.archfixture;

import com.aresstack.enterpriseai.source.api.archfixture.FakeKnowledgeSourcePort;

/** Absichtlicher Verstoß: RAG kennt den Source-Port (RagBoundaryTest). */
public final class RagUsingSource {

    public FakeKnowledgeSourcePort source() {
        return null;
    }
}
