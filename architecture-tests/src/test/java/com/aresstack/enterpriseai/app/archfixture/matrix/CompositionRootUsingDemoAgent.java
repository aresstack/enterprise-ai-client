package com.aresstack.enterpriseai.app.archfixture.matrix;

import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;

/** Absichtlicher Verstoß: auch app-swing darf acp-demo-agent nicht kennen. */
public final class CompositionRootUsingDemoAgent {

    private final FakeDemoAgent target = new FakeDemoAgent();

    public Object use() {
        return target.name();
    }
}
