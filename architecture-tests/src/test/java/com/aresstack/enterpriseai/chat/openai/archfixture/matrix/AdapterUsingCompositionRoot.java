package com.aresstack.enterpriseai.chat.openai.archfixture.matrix;

import com.aresstack.enterpriseai.app.archfixture.FakeShell;

/** Absichtlicher Verstoß: ein Adapter kennt app-swing. */
public final class AdapterUsingCompositionRoot {

    private final FakeShell target = new FakeShell();

    public Object use() {
        return target.title();
    }
}
