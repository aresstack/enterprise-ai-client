package com.aresstack.enterpriseai.application.archfixture.config;

import java.util.ServiceLoader;

/** Absichtlicher Verstoß: ServiceLoader statt Konstruktor-Injektion. */
public final class UseCaseUsingServiceLoader {

    public Iterable<Runnable> plugins() {
        return ServiceLoader.load(Runnable.class);
    }
}
