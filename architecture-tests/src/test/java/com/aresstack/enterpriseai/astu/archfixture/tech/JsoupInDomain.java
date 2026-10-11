package com.aresstack.enterpriseai.astu.archfixture.tech;

import org.jsoup.archstub.JsoupStub;

/** Absichtlicher Verstoß: astu kennt jsoup. */
public final class JsoupInDomain {

    private final JsoupStub jsoup = new JsoupStub();

    public String describe() {
        return jsoup.describe();
    }
}
