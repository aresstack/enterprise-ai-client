package com.aresstack.enterpriseai.ui.comic.archfixture;

import com.aresstack.enterpriseai.app.ui.archfixture.UiCallingPort;

/** Absichtlicher Verstoß: Die Comic-Bibliothek kennt Anwendungstypen. */
public final class ComicKnowingTheApp {

    public Object app() {
        return new UiCallingPort();
    }
}
