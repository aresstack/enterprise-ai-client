package com.aresstack.enterpriseai.chat.api.archfixture.matrix;

import com.aresstack.enterpriseai.astu.acropolis.archfixture.NeutralUseCase;

/** Absichtlicher Verstoß: ein Port kennt application. */
public final class PortUsingApplication {

    private final NeutralUseCase target = new NeutralUseCase();

    public Object use() {
        return target.run();
    }
}
