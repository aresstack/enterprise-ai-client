package com.aresstack.enterpriseai.security.keepassrpc.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Gegenprobe: der WebSocket-Transport in security-keepassrpc ist erlaubt. */
public final class WebSocketInsideKeePass {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
