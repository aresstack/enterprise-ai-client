package com.aresstack.enterpriseai.embedding.openai.archfixture.signature;

import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: öffentliche Adapterklasse hängt von Gson ab (EmbeddingBoundaryTest). */
public final class AdapterExposingGson {

    public GsonStub json() {
        return new GsonStub();
    }
}
