package com.aresstack.enterpriseai.app.ui.archfixture.web;

import javax.servlet.archstub.ServletStub;

/** Absichtlicher Verstoß (Nachtrag 3): Web-Frontend. */
public final class ServletFrontend {

    private final ServletStub servlet = new ServletStub();

    public String describe() {
        return servlet.describe();
    }
}
