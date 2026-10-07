package com.aresstack.enterpriseai.application.archfixture.testleak;

import com.aresstack.enterpriseai.mcp.api.testkit.archfixture.FakeToolkit;

/** Absichtlicher Verstoß: Produktionscode benutzt ein testkit-/testing-/fake-Paket. */
public final class UseCaseUsingTestkitPackage {

    private final FakeToolkit target = new FakeToolkit();

    public Object use() {
        return target.tool();
    }
}
