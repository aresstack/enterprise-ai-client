package com.aresstack.enterpriseai.embedding.api.archfixture;

/** Steht für den Embedding-Port als Ziel von Gegenbeispielen. */
public interface FakeEmbeddingPort {

    float[] embed(String text);
}
