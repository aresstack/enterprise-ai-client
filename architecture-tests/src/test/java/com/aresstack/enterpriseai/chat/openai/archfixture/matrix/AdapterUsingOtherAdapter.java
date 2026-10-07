package com.aresstack.enterpriseai.chat.openai.archfixture.matrix;

import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: ein Adapter kennt einen anderen Adapter. */
public final class AdapterUsingOtherAdapter {

    private final FakeLuceneAdapter target = new FakeLuceneAdapter();

    public Object use() {
        return target.search("q");
    }
}
