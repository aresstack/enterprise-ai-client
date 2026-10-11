package com.aresstack.enterpriseai.astu.acropolis.archfixture.matrix;

import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;

/** Absichtlicher Verstoß: acropolis kennt acp-demo-agent. */
public final class UseCaseUsingDemoAgent {

    private final FakeDemoAgent target = new FakeDemoAgent();

    public Object use() {
        return target.name();
    }
}
