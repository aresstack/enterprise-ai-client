package com.aresstack.enterpriseai.astu.archfixture.matrix;

import com.aresstack.enterpriseai.astu.acropolis.archfixture.NeutralUseCase;

/** Absichtlicher Verstoß: astu kennt application. */
public final class DomainUsingApplication {

    private final NeutralUseCase target = new NeutralUseCase();

    public Object use() {
        return target.run();
    }
}
