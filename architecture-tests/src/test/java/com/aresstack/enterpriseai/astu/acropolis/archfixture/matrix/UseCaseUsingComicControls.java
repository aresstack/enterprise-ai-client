package com.aresstack.enterpriseai.astu.acropolis.archfixture.matrix;

import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Absichtlicher Verstoß: acropolis kennt comic-controls. */
public final class UseCaseUsingComicControls {

    private final FakeComicPanel target = new FakeComicPanel();

    public Object use() {
        return target.style();
    }
}
