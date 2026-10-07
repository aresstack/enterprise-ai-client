package com.aresstack.enterpriseai.application.archfixture.agentleak;

import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;

/** Absichtlicher Verstoß: ein Use Case außerhalb von application.agent benutzt ACP. */
public final class UseCaseUsingAcp {

    public String session() {
        return new FakeAcpSession().sessionId();
    }
}
