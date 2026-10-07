package com.aresstack.enterpriseai.domain.archfixture;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Absichtlicher Verstoß: HTTP im Domain-Paket. Nur für RulesDetectViolationsTest. */
public final class HttpInDomain {

    public HttpURLConnection open(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }
}
