package com.aresstack.enterpriseai.chat.openai.archfixture.matrix;

import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;

/** Absichtlicher Verstoß: ein Modul kennt acp-demo-agent. */
public final class AdapterUsingDemoAgent {

    private final FakeDemoAgent target = new FakeDemoAgent();

    public Object use() {
        return target.name();
    }
}
