package com.aresstack.enterpriseai.astu.archfixture.tech;

import com.aresstack.enterpriseai.proasteion.emporion.holkas.mediawiki.archfixture.FakeWikiAdapter;

/** Absichtlicher Verstoß: astu kennt den MediaWiki-Adapter. */
public final class MediaWikiAdapterInDomain {

    private final FakeWikiAdapter target = new FakeWikiAdapter();

    public Object use() {
        return target.site();
    }
}
