package com.aresstack.enterpriseai.security.keepassrpc.archfixture.signature;

import org.java_websocket.archstub.WebSocketStub;

/** Absichtlicher Verstoß: öffentliche Methode liefert einen Transporttyp. */
public final class AdapterExposingWebSocket {

    public WebSocketStub socket() {
        return null;
    }
}
