package com.aresstack.enterpriseai.acp.demo.archfixture.matrix;

import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;

/** Absichtlicher Verstoß: der Demo-Agent kennt ein Projektmodul. */
public final class DemoAgentUsingPort {

    private final FakeAcpSession target = new FakeAcpSession();

    public Object use() {
        return target.sessionId();
    }
}
