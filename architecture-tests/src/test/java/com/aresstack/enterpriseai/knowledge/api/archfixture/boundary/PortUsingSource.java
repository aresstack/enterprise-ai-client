package com.aresstack.enterpriseai.knowledge.api.archfixture.boundary;

import com.aresstack.enterpriseai.source.api.archfixture.FakeKnowledgeSourcePort;

/** Absichtlicher Verstoß: der Index-Port kennt den Source-Port (KnowledgeBoundaryTest). */
public final class PortUsingSource {

    public FakeKnowledgeSourcePort source() {
        return null;
    }
}
