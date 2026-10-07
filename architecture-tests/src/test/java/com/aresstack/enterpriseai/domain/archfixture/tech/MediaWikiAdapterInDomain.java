package com.aresstack.enterpriseai.domain.archfixture.tech;

import com.aresstack.enterpriseai.source.mediawiki.archfixture.FakeWikiAdapter;

/** Absichtlicher Verstoß: domain kennt den MediaWiki-Adapter. */
public final class MediaWikiAdapterInDomain {

    private final FakeWikiAdapter target = new FakeWikiAdapter();

    public Object use() {
        return target.site();
    }
}
