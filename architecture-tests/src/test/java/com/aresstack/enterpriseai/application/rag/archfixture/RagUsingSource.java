package com.aresstack.enterpriseai.application.rag.archfixture;

import com.aresstack.enterpriseai.source.api.archfixture.FakeSourcePort;

/** Absichtlicher Verstoß: RAG kennt den Source-Port (RagBoundaryTest). */
public final class RagUsingSource {

    public FakeSourcePort source() {
        return null;
    }
}
