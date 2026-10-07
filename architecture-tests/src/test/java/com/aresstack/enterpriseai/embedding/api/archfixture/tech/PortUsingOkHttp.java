package com.aresstack.enterpriseai.embedding.api.archfixture.tech;

import okhttp3.archstub.OkHttpStub;

/** Absichtlicher Verstoß: embedding-api kennt OkHttp. */
public final class PortUsingOkHttp {

    private final OkHttpStub okHttp = new OkHttpStub();

    public String describe() {
        return okHttp.describe();
    }
}
