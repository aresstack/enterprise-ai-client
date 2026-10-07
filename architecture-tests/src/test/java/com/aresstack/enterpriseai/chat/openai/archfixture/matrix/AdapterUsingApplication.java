package com.aresstack.enterpriseai.chat.openai.archfixture.matrix;

import com.aresstack.enterpriseai.application.archfixture.NeutralUseCase;

/** Absichtlicher Verstoß: ein Adapter kennt application. */
public final class AdapterUsingApplication {

    private final NeutralUseCase target = new NeutralUseCase();

    public Object use() {
        return target.run();
    }
}
