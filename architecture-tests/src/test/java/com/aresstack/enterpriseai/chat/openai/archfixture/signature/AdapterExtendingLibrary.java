package com.aresstack.enterpriseai.chat.openai.archfixture.signature;

import com.google.gson.archstub.GsonStub;

/** Absichtlicher Verstoß: öffentliche Adapterklasse erbt von einem Bibliothekstyp. */
public final class AdapterExtendingLibrary extends GsonStub {

    public String adapter() {
        return "leaks supertype";
    }
}
