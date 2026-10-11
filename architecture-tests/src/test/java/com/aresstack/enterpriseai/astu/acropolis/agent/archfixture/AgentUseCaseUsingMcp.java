package com.aresstack.enterpriseai.astu.acropolis.agent.archfixture;

import com.aresstack.enterpriseai.proasteion.katagogion.archfixture.FakeMcpRegistry;

/** Absichtlicher Verstoß: der Agent-Use-Case kennt MCP (AgentModeBoundaryTest). */
public final class AgentUseCaseUsingMcp {

    private final FakeMcpRegistry target = new FakeMcpRegistry();

    public Object use() {
        return target.endpoint();
    }
}
