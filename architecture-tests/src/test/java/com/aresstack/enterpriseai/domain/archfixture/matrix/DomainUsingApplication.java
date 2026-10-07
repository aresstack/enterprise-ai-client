package com.aresstack.enterpriseai.domain.archfixture.matrix;

import com.aresstack.enterpriseai.application.archfixture.NeutralUseCase;

/** Absichtlicher Verstoß: domain kennt application. */
public final class DomainUsingApplication {

    private final NeutralUseCase target = new NeutralUseCase();

    public Object use() {
        return target.run();
    }
}
