package com.aresstack.enterpriseai.application.archfixture.matrix;

import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Absichtlicher Verstoß: application kennt comic-controls. */
public final class UseCaseUsingComicControls {

    private final FakeComicPanel target = new FakeComicPanel();

    public Object use() {
        return target.style();
    }
}
