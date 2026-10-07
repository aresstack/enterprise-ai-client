package com.aresstack.enterpriseai.source.mediawiki.archfixture.tech;

import net.sourceforge.jwbf.archstub.JwbfStub;

/** Gegenprobe: JWBF in source-mediawiki ist erlaubt. */
public final class JwbfInsideMediaWiki {

    private final JwbfStub jwbf = new JwbfStub();

    public String describe() {
        return jwbf.describe();
    }
}
