package com.aresstack.enterpriseai.domain.archfixture.tech;

import okhttp3.archstub.OkHttpStub;

/** Absichtlicher Verstoß: domain kennt OkHttp. */
public final class OkHttpInDomain {

    private final OkHttpStub okHttp = new OkHttpStub();

    public String describe() {
        return okHttp.describe();
    }
}
