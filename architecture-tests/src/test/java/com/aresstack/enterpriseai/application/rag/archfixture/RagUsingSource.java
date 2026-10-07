package com.aresstack.enterpriseai.application.rag.archfixture;

import com.aresstack.enterpriseai.source.api.archfixture.FakeKnowledgeSourcePort;

/** Absichtlicher Verstoß: RAG kennt den Source-Port (RagBoundaryTest). */
public final class RagUsingSource {

    public FakeKnowledgeSourcePort source() {
        return null;
    }
}
