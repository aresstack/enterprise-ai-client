package com.aresstack.enterpriseai.astu.archfixture.tech;

import okhttp3.archstub.OkHttpStub;

/** Absichtlicher Verstoß: astu kennt OkHttp. */
public final class OkHttpInDomain {

    private final OkHttpStub okHttp = new OkHttpStub();

    public String describe() {
        return okHttp.describe();
    }
}
