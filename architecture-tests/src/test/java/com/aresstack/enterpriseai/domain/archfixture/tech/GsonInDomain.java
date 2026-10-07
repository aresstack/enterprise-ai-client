package com.aresstack.enterpriseai.domain.archfixture.tech;

import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: domain kennt Gson. */
public final class GsonInDomain {

    private final GsonStub gson = new GsonStub();

    public String describe() {
        return gson.describe();
    }
}
