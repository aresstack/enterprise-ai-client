package com.aresstack.enterpriseai.source.api.archfixture;

import com.aresstack.enterpriseai.proasteion.emporion.holkas.mediawiki.archfixture.FakeWikiAdapter;

/** Absichtlicher Verstoß: source-api kennt einen Source-Adapter (SourceBoundaryTest). */
public final class PortKnowingAdapter {

    private final FakeWikiAdapter target = new FakeWikiAdapter();

    public Object use() {
        return target.site();
    }
}
