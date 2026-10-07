package com.aresstack.enterpriseai.chat.api.archfixture.matrix;

import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;

/** Absichtlicher Verstoß: chat-api kennt chat-openai. */
public final class PortUsingItsAdapter {

    private final FakeChatAdapter target = new FakeChatAdapter();

    public Object use() {
        return target.model();
    }
}
