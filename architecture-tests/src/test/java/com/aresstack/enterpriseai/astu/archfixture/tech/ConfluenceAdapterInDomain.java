package com.aresstack.enterpriseai.astu.archfixture.tech;

import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.archfixture.FakeConfluenceAdapter;

/** Absichtlicher Verstoß: astu kennt den Confluence-Adapter. */
public final class ConfluenceAdapterInDomain {

    private final FakeConfluenceAdapter target = new FakeConfluenceAdapter();

    public Object use() {
        return target.space();
    }
}
