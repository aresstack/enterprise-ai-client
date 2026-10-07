package com.aresstack.enterpriseai.chat.openai.archfixture.logging;

import org.slf4j.archstub.Slf4jStub;

/** Absichtlicher Verstoß: Logging-Framework im Produktionscode. */
public final class AdapterUsingSlf4j {

    private final Slf4jStub log = new Slf4jStub();

    public String describe() {
        return log.describe();
    }
}
