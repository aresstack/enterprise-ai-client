package com.aresstack.enterpriseai.astu.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: astu kennt den KeePassRPC-Transport. */
public final class WebSocketInDomain {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
