package com.aresstack.enterpriseai.astu.archfixture.matrix;

import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;

/** Absichtlicher Verstoß: astu kennt einen Port. */
public final class DomainUsingPort {

    public FakeChatPort port() {
        return null;
    }
}
