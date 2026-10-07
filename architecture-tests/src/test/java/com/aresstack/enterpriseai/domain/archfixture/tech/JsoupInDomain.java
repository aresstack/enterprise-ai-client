package com.aresstack.enterpriseai.domain.archfixture.tech;

import org.jsoup.archstub.JsoupStub;

/** Absichtlicher Verstoß: domain kennt jsoup. */
public final class JsoupInDomain {

    private final JsoupStub jsoup = new JsoupStub();

    public String describe() {
        return jsoup.describe();
    }
}
