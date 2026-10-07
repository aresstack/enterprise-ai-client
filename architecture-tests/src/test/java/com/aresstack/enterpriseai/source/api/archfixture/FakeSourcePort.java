package com.aresstack.enterpriseai.source.api.archfixture;

/** Steht in den Selbsttests von McpKnowledgeToolsBoundaryTest für den Quell-Port bzw. seinen Scope (source-api). */
public final class FakeSourcePort {

    public int discover(String scope) {
        return scope.length();
    }
}
