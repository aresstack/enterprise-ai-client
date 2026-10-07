package com.aresstack.enterpriseai.domain.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: domain kennt den KeePassRPC-Transport. */
public final class WebSocketInDomain {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
