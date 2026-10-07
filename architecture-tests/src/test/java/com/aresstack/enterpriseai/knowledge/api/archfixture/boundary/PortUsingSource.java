package com.aresstack.enterpriseai.knowledge.api.archfixture.boundary;

import com.aresstack.enterpriseai.source.api.archfixture.FakeSourcePort;

/** Absichtlicher Verstoß: der Index-Port kennt den Source-Port (KnowledgeBoundaryTest). */
public final class PortUsingSource {

    public FakeSourcePort source() {
        return null;
    }
}
