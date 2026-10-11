package com.aresstack.enterpriseai.proasteion.application.composition.archfixture.matrix;

import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Absichtlicher Verstoß: der Kompositionspunkt kennt keine Oberfläche (corenth ADR-0001, Leitplanke 3). */
public final class CompositionRootUsingComicControls {

    private final FakeComicPanel panel = new FakeComicPanel();

    public Object use() {
        return panel.style();
    }
}
