package com.aresstack.enterpriseai.astu.archfixture.tech;

import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: astu kennt Gson. */
public final class GsonInDomain {

    private final GsonStub gson = new GsonStub();

    public String describe() {
        return gson.describe();
    }
}
