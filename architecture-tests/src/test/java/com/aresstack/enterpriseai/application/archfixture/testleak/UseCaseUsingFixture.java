package com.aresstack.enterpriseai.application.archfixture.testleak;

import com.aresstack.enterpriseai.chat.api.archfixture.testleak.FakeTestFixturePort;

/** Absichtlicher Verstoß: Produktionscode benutzt eine Testfixture-Klasse. */
public final class UseCaseUsingFixture {

    private final FakeTestFixturePort target = new FakeTestFixturePort();

    public Object use() {
        return target.fake();
    }
}
