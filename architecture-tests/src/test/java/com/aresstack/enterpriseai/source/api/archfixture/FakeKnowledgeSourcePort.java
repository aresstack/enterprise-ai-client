package com.aresstack.enterpriseai.source.api.archfixture;

/** Steht für den Source-Port als Ziel von Gegenbeispielen (AP24). */
public interface FakeKnowledgeSourcePort {

    String load(String id);
}
