package com.aresstack.enterpriseai.chat.openai.archfixture.signature;

import com.google.gson.archstub.GsonStub;

/** Gegenprobe: Bibliothekstypen nur in privaten Feldern und paketprivaten Methoden sind erlaubt. */
public final class AdapterHidingJson {

    private final GsonStub json = new GsonStub();

    GsonStub json() {
        return json;
    }

    public String describe() {
        return json.describe();
    }
}
