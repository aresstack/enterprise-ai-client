package com.aresstack.enterpriseai.source.mediawiki.archfixture.tech;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: KeePassRPC-Transport außerhalb von security-keepassrpc. */
public final class WebSocketInMediaWiki {

    private final WebSocketStub webSocket = new WebSocketStub();

    public String describe() {
        return webSocket.describe();
    }
}
