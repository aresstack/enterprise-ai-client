package com.aresstack.enterpriseai.domain.archfixture.matrix;

import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;

/** Absichtlicher Verstoß: domain kennt einen Port. */
public final class DomainUsingPort {

    public FakeChatPort port() {
        return null;
    }
}
