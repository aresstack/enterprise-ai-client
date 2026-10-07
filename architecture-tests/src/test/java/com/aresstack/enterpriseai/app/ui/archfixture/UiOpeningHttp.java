package com.aresstack.enterpriseai.app.ui.archfixture;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Absichtlicher Verstoß: Die Oberfläche baut selbst eine HTTP-Verbindung auf. */
public final class UiOpeningHttp {

    public HttpURLConnection open(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }
}
