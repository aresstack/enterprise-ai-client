package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Echter HTTP-Weg gegen einen lokalen {@link HttpServer} (ohne Netz nach außen). */
public class UrlConnectionConfluenceTransportTest {

    private HttpServer server;
    private URI base;
    private final AtomicReference<String> seenAuthorization = new AtomicReference<String>();
    private final AtomicReference<String> seenUri = new AtomicReference<String>();
    private final AtomicReference<String> seenUserAgent = new AtomicReference<String>();
    private final UrlConnectionConfluenceTransport transport =
            UrlConnectionConfluenceTransport.builder().proxy(Proxy.NO_PROXY).readTimeoutMillis(5000).build();

    @Before
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> {
            seenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenUri.set(exchange.getRequestURI().toString());
            seenUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, "application/json", "{\"a\":1}");
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://elsewhere.invalid/login");
            respond(exchange, 302, "text/html", "");
        });
        server.createContext("/missing", exchange -> respond(exchange, 404, "application/json", "{\"statusCode\":404}"));
        server.createContext("/big", exchange -> respond(exchange, 200, "text/plain", new String(new char[20000]).replace('\0', 'x')));
        server.start();
        base = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    @After
    public void stop() {
        server.stop(0);
    }

    @Test
    public void sendsHeadersAndReadsTheBody() throws IOException {
        ConfluenceHttpResponse response = transport.get(base.resolve("/ok"), headers(), 1024);
        assertEquals(200, response.status());
        assertTrue(response.contentType().startsWith("application/json"));
        assertEquals("{\"a\":1}", response.bodyAsUtf8());
        assertEquals("Basic dGVzdA==", seenAuthorization.get());
        assertFalse(response.toString().contains("\"a\""));
    }

    @Test
    public void doesNotFollowRedirects() throws IOException {
        assertEquals(302, transport.get(base.resolve("/redirect"), headers(), 1024).status());
    }

    @Test
    public void errorStatusesAreResponsesWithBody() throws IOException {
        ConfluenceHttpResponse response = transport.get(base.resolve("/missing"), headers(), 1024);
        assertEquals(404, response.status());
        assertEquals("{\"statusCode\":404}", response.bodyAsUtf8());
    }

    @Test
    public void rejectsBodiesAboveTheLimit() {
        try {
            transport.get(base.resolve("/big"), headers(), 1000);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("1000"));
        }
    }

    @Test(expected = IOException.class)
    public void rejectsOtherSchemes() throws IOException {
        transport.get(URI.create("file:///etc/passwd"), headers(), 1000);
    }

    @Test
    public void toStringShowsProxyAndTlsButNoHeaders() {
        assertEquals("UrlConnectionConfluenceTransport[proxy=DIRECT, tls=false]", transport.toString());
    }

    @Test
    public void aProxyRouteSendsTheAbsoluteUrlToTheProxyAndTheUserAgent() throws IOException {
        final int port = server.getAddress().getPort();
        HttpRoutePort viaProxy = new HttpRoutePort() {
            @Override
            public HttpRoute routeFor(URI target) {
                return HttpRoute.proxy("127.0.0.1", port, "test");
            }
        };
        UrlConnectionConfluenceTransport proxied = UrlConnectionConfluenceTransport.builder()
                .routes(viaProxy).userAgent("EnterpriseAiClient/test").readTimeoutMillis(5000).build();
        ConfluenceHttpResponse response = proxied.get(URI.create("http://confluence.intern.invalid/ok"), headers(), 1024);
        assertEquals(200, response.status());
        assertEquals("http://confluence.intern.invalid/ok", seenUri.get());
        assertEquals("EnterpriseAiClient/test", seenUserAgent.get());
        assertEquals("UrlConnectionConfluenceTransport[proxy=" + viaProxy + ", tls=false]", proxied.toString());
    }

    @Test
    public void anUnavailableRouteFailsBeforeAnyRequest() {
        HttpRoutePort unavailable = new HttpRoutePort() {
            @Override
            public HttpRoute routeFor(URI target) {
                return HttpRoute.unavailable("pac-download-failed", "HTTP 404");
            }
        };
        UrlConnectionConfluenceTransport blocked = UrlConnectionConfluenceTransport.builder().routes(unavailable).build();
        try {
            blocked.get(base.resolve("/ok"), headers(), 1024);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("pac-download-failed"));
        }
        assertEquals(null, seenUri.get());
    }

    @Test
    public void aDirectRouteTakesPrecedenceOverAFixedProxy() throws IOException {
        UrlConnectionConfluenceTransport direct = UrlConnectionConfluenceTransport.builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getLoopbackAddress(), 1)))
                .routes(HttpRoutePort.direct()).readTimeoutMillis(5000).build();
        assertEquals(200, direct.get(base.resolve("/ok"), headers(), 1024).status());
        assertEquals("/ok", seenUri.get());
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic dGVzdA==");
        return Collections.unmodifiableMap(headers);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
