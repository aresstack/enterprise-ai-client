package com.aresstack.enterpriseai.application.agent.archfixture;

import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;

/** Erlaubt: der Agent-Use-Case darf ACP benutzen. Gegenprobe für AgentModeBoundaryTest. */
public final class AgentUseCaseUsingAcp {

    public String session() {
        return new FakeAcpSession().sessionId();
    }
}
