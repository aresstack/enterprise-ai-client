package com.aresstack.enterpriseai.knowledge.api.archfixture;

/** Steht in den Selbsttests von McpKnowledgeToolsBoundaryTest für den Index-Port (knowledge-api). */
public final class FakeIndexPort {

    public int search(String query) {
        return query.length();
    }
}
