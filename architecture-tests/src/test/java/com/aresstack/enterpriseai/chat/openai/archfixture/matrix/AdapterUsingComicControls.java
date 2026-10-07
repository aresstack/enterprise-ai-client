package com.aresstack.enterpriseai.chat.openai.archfixture.matrix;

import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Absichtlicher Verstoß: ein Adapter kennt comic-controls. */
public final class AdapterUsingComicControls {

    private final FakeComicPanel target = new FakeComicPanel();

    public Object use() {
        return target.style();
    }
}
