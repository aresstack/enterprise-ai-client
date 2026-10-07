package com.aresstack.enterpriseai.knowledge.api.archfixture;

/** Steht für den Index-Port als Ziel von Gegenbeispielen (AP24). */
public interface FakeKnowledgeIndexPort {

    void index(String id, String text);
}
