package com.aresstack.enterpriseai.domain.archfixture.tech;

import com.aresstack.enterpriseai.source.confluence.archfixture.FakeConfluenceAdapter;

/** Absichtlicher Verstoß: domain kennt den Confluence-Adapter. */
public final class ConfluenceAdapterInDomain {

    private final FakeConfluenceAdapter target = new FakeConfluenceAdapter();

    public Object use() {
        return target.space();
    }
}
