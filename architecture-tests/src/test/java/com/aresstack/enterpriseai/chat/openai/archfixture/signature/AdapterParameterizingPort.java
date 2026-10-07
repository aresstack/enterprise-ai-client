package com.aresstack.enterpriseai.chat.openai.archfixture.signature;

import com.aresstack.enterpriseai.chat.api.archfixture.FakeGenericPort;
import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: der Bibliothekstyp steckt nur im Typargument des implementierten Ports. */
public final class AdapterParameterizingPort implements FakeGenericPort<GsonStub> {

    @Override
    public String describe() {
        return "leaks type argument";
    }
}
