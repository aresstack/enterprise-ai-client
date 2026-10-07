package com.aresstack.enterpriseai.application.agent.archfixture;

import com.aresstack.enterpriseai.mcp.api.archfixture.FakeMcpRegistry;

/** Absichtlicher Verstoß: der Agent-Use-Case kennt MCP (AgentModeBoundaryTest). */
public final class AgentUseCaseUsingMcp {

    private final FakeMcpRegistry target = new FakeMcpRegistry();

    public Object use() {
        return target.endpoint();
    }
}
