package com.aresstack.enterpriseai.application.archfixture.matrix;

import com.aresstack.enterpriseai.app.archfixture.FakeShell;

/** Absichtlicher Verstoß: application kennt app-swing. */
public final class UseCaseUsingCompositionRoot {

    private final FakeShell target = new FakeShell();

    public Object use() {
        return target.title();
    }
}
