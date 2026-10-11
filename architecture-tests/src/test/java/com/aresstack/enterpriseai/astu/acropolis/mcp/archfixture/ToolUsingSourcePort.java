package com.aresstack.enterpriseai.astu.acropolis.mcp.archfixture;

import com.aresstack.enterpriseai.source.api.archfixture.FakeSourcePort;

/** Absichtlicher Verstoß: ein MCP-Werkzeug nimmt den Quell-Port (oder seinen Scope) selbst in die Hand. */
public final class ToolUsingSourcePort {

    public int invoke(String scope) {
        return new FakeSourcePort().discover(scope);
    }
}
