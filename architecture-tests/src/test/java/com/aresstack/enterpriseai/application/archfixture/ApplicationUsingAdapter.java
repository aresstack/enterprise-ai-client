package com.aresstack.enterpriseai.application.archfixture;

import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: Application kennt einen konkreten Adapter. Nur für RulesDetectViolationsTest. */
public final class ApplicationUsingAdapter {

    private final FakeLuceneAdapter adapter = new FakeLuceneAdapter();

    public String ask(String question) {
        return adapter.search(question);
    }
}
