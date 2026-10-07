package com.aresstack.enterpriseai.application.archfixture.matrix;

import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;

/** Absichtlicher Verstoß: application kennt acp-demo-agent. */
public final class UseCaseUsingDemoAgent {

    private final FakeDemoAgent target = new FakeDemoAgent();

    public Object use() {
        return target.name();
    }
}
