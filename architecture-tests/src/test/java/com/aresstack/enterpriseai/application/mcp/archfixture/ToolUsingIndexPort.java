package com.aresstack.enterpriseai.application.mcp.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPort;

/** Absichtlicher Verstoß: ein MCP-Werkzeug greift am Use Case vorbei direkt auf den Index-Port zu. */
public final class ToolUsingIndexPort {

    public int invoke(String query) {
        return new FakeIndexPort().search(query);
    }
}
