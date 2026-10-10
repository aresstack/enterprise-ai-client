package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Übersetzt die Route des {@link HttpRoutePort}s in den {@link ProxySelector}, den huggingface4j je Anfrage
 * (auch nach Weiterleitungen zum CDN) fragt. Nur dieser Hub-Client bekommt ihn, kein prozessweiter Selector.
 * Eine nicht verfügbare Route wird nie still zur Direktverbindung: die Anfrage scheitert mit dem Grund.
 */
final class RouteProxySelector extends ProxySelector {

    private final HttpRoutePort routes;

    RouteProxySelector(HttpRoutePort routes) {
        this.routes = routes;
    }

    @Override
    public List<Proxy> select(URI uri) {
        HttpRoute route = routes.routeFor(uri);
        if (route == null) {
            throw new IllegalStateException("no route decision for " + uri.getHost());
        }
        switch (route.kind()) {
            case DIRECT:
                return Collections.singletonList(Proxy.NO_PROXY);
            case PROXY:
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(route.proxyHost(), route.proxyPort())));
            default:
                throw new IllegalStateException("proxy route for " + uri.getHost() + " unavailable: "
                        + route.describe());
        }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        // Die Route entscheidet HttpRoutes; ein Fehlschlag meldet die Anfrage selbst.
    }
}
