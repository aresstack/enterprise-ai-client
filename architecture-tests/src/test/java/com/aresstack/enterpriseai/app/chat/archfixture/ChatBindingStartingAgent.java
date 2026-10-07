package com.aresstack.enterpriseai.app.chat.archfixture;

import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;

/** Absichtlicher Verstoß: Die Chat-Anbindung greift auf ACP zu. Der Chat muss ohne Agent funktionieren. */
public final class ChatBindingStartingAgent {

    public String session() {
        return new FakeAcpSession().sessionId();
    }
}
