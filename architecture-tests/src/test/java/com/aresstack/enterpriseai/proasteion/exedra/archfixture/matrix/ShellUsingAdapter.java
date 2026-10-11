package com.aresstack.enterpriseai.proasteion.exedra.archfixture.matrix;

import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;

/** Absichtlicher Verstoß: die Schale (exedra) kennt keinen Adapter, nur den Kompositionspunkt (corenth ADR-0001). */
public final class ShellUsingAdapter {

    private final FakeChatAdapter adapter = new FakeChatAdapter();

    public Object use() {
        return adapter.model();
    }
}
