package com.aresstack.enterpriseai.chat.openai.archfixture.signature;

import java.net.HttpURLConnection;

/** Absichtlicher Verstoß: HTTP-Verbindung in einer öffentlichen Signatur (ChatBoundaryTest, Adapter-Signaturregel). */
public final class AdapterLeakingConnection {

    public HttpURLConnection connection() {
        return null;
    }
}
