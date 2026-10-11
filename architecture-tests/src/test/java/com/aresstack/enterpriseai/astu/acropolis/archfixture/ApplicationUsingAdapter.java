package com.aresstack.enterpriseai.astu.acropolis.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: Application kennt einen konkreten Adapter. Nur für RulesDetectViolationsTest. */
public final class ApplicationUsingAdapter {

    private final FakeLuceneAdapter adapter = new FakeLuceneAdapter();

    public String ask(String question) {
        return adapter.search(question);
    }
}
