package com.aresstack.enterpriseai.proasteion.exedra.archfixture.matrix;

import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;

/** Absichtlicher Verstoß: auch exedra darf acp-demo-agent nicht kennen. */
public final class CompositionRootUsingDemoAgent {

    private final FakeDemoAgent target = new FakeDemoAgent();

    public Object use() {
        return target.name();
    }
}
