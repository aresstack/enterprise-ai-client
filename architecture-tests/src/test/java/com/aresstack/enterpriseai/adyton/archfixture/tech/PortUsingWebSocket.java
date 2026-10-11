package com.aresstack.enterpriseai.adyton.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: adyton kennt den KeePassRPC-Transport. */
public final class PortUsingWebSocket {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
