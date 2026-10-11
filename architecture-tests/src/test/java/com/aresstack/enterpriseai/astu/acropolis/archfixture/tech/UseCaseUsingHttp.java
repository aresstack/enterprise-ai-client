package com.aresstack.enterpriseai.astu.acropolis.archfixture.tech;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Absichtlicher Verstoß: acropolis öffnet HTTP-Verbindungen. */
public final class UseCaseUsingHttp {

    public HttpURLConnection open(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }
}
