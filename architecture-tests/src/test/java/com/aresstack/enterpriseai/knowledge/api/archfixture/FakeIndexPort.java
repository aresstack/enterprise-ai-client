package com.aresstack.enterpriseai.knowledge.api.archfixture;

/** Steht für den Index-Port als Ziel von Gegenbeispielen. */
public interface FakeIndexPort {

    void index(String id, String text);
}
