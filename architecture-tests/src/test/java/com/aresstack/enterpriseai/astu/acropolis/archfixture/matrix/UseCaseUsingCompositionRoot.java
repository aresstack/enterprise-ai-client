package com.aresstack.enterpriseai.astu.acropolis.archfixture.matrix;

import com.aresstack.enterpriseai.proasteion.exedra.archfixture.FakeShell;

/** Absichtlicher Verstoß: acropolis kennt exedra. */
public final class UseCaseUsingCompositionRoot {

    private final FakeShell target = new FakeShell();

    public Object use() {
        return target.title();
    }
}
