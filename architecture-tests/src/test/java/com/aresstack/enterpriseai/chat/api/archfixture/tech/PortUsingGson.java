package com.aresstack.enterpriseai.chat.api.archfixture.tech;

import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: der Chat-Port kennt Gson. */
public final class PortUsingGson {

    private final GsonStub gson = new GsonStub();

    public String describe() {
        return gson.describe();
    }
}
