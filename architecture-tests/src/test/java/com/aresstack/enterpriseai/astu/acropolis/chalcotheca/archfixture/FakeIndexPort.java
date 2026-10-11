package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture;

/** Steht in den Selbsttests von McpKnowledgeToolsBoundaryTest für den Index-Port (chalcotheca). */
public final class FakeIndexPort {

    public int search(String query) {
        return query.length();
    }
}
