package com.aresstack.enterpriseai.security.api.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: security-api kennt den KeePassRPC-Transport. */
public final class PortUsingWebSocket {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
