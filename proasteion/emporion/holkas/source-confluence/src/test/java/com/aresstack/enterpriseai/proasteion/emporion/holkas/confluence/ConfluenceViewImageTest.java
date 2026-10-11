package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.PageView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Angelos Live-Fehler „Bild konnte nicht geladen werden“: Der Bildrequest fragt nach Bildern, und jede Nicht-Bild-
 * Antwort wird unmittelbar nach HTTP mit eindeutigem Grund abgelehnt und mit URL, Status, Content-Type, Größe und
 * Fehlerart (ohne Header) protokolliert.
 */
public class ConfluenceViewImageTest {

    private static final String BASE = "https://confluence.invalid/confluence";
    private static final String IMAGE = BASE + "/download/attachments/7/bild.png?version=1&api=v2";
    /** {@code export_view} schreibt Bildadressen absolut mit der Server-Basis, die Confluence für sich eingestellt hat. */
    private static final String SERVER_BASE = "http://wiki.invalid:8090/confluence";
    private static final String READING_PAGE = "{\"id\":\"7\",\"type\":\"page\",\"title\":\"Seite\","
            + "\"body\":{\"export_view\":{\"value\":\"<p><img src=\\\"" + SERVER_BASE
            + "/download/attachments/7/a.png?version=1&amp;api=v2\\\"></p>\"}},"
            + "\"_links\":{\"base\":\"" + SERVER_BASE + "\",\"webui\":\"/pages/7\"}}";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};

    private final RecordingSecretProvider secrets = new RecordingSecretProvider();
    private final List<String> logLines = new ArrayList<String>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                logLines.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private URI requested;
    private Map<String, String> headers;
    private final List<URI> requests = new ArrayList<URI>();
    private final List<Map<String, String>> sentHeaders = new ArrayList<Map<String, String>>();
    private final Map<String, ConfluenceHttpResponse> byHost = new java.util.HashMap<String, ConfluenceHttpResponse>();
    private ConfluenceHttpResponse response;
    private IOException failure;

    @Before
    public void captureLog() {
        Logger.getLogger(ConfluenceRestClient.class.getName()).addHandler(capture);
        Logger.getLogger(ConfluenceKnowledgeSource.class.getName()).addHandler(capture);
    }

    @After
    public void releaseLog() {
        Logger.getLogger(ConfluenceRestClient.class.getName()).removeHandler(capture);
        Logger.getLogger(ConfluenceKnowledgeSource.class.getName()).removeHandler(capture);
    }

    private ConfluenceKnowledgeSource source(int maxAttachmentBytes) {
        ConfluenceHttpTransport transport = (uri, sent, maxBytes) -> {
            requested = uri;
            headers = sent;
            requests.add(uri);
            sentHeaders.add(sent);
            if (failure != null) {
                throw failure;
            }
            if (uri.getRawPath().startsWith("/confluence/rest/api/content/7")) {
                return new ConfluenceHttpResponse(200, "application/json", READING_PAGE.getBytes(StandardCharsets.UTF_8));
            }
            ConfluenceHttpResponse hosted = byHost.get(uri.getHost());
            return hosted != null ? hosted : response;
        };
        return new ConfluenceKnowledgeSource(SourceAddress.of("confluence-dc"),
                ConfluenceConfig.builder(URI.create(BASE)).credentialRef(RecordingSecretProvider.REF)
                        .maxAttachmentBytes(maxAttachmentBytes).build(), transport, secrets);
    }

    private ConfluenceKnowledgeSource source() {
        return source(5 * 1024 * 1024);
    }

    @Test
    public void theImageRequestAsksForImagesNotJson() throws Exception {
        response = new ConfluenceHttpResponse(200, "image/png", PNG);

        assertArrayEquals(PNG, source().loadViewImage(IMAGE));

        assertEquals(IMAGE, requested.toString());
        assertEquals("image/*,*/*;q=0.8", headers.get("Accept"));
        assertTrue("angemeldet wie alle Aufrufe", headers.containsKey("Authorization"));
        assertTrue(logLines.isEmpty());
    }

    @Test
    public void anOctetStreamWithImageBytesIsAnImage() throws Exception {
        response = new ConfluenceHttpResponse(200, "application/octet-stream", PNG);

        assertArrayEquals(PNG, source().loadViewImage(IMAGE));
    }

    @Test
    public void jsonIsRejectedRightAfterHttpWithItsContentType() {
        response = new ConfluenceHttpResponse(200, "application/json;charset=UTF-8",
                "{\"statusCode\":200}".getBytes(StandardCharsets.UTF_8));

        KnowledgeSourceException e = failing();

        assertEquals(Kind.INVALID_RESPONSE, e.kind());
        assertEquals("Bild: erwartet Bild, erhalten application/json (HTTP 200, 18 Bytes)", e.getMessage());
    }

    @Test
    public void aLoginPageIsNamedAsSuch() {
        response = new ConfluenceHttpResponse(200, "text/html; charset=UTF-8",
                "<html><body>Anmelden</body></html>".getBytes(StandardCharsets.UTF_8));

        KnowledgeSourceException e = failing();

        assertEquals(Kind.INVALID_RESPONSE, e.kind());
        assertTrue(e.getMessage(), e.getMessage().startsWith(
                "Bild: erwartet Bild, erhalten text/html (vermutlich eine Anmeldeseite)"));
    }

    @Test
    public void anImageOutsideTheConfiguredBaseIsNeverRequestedAndSaysWhy() {
        String foreign = "https://other.invalid/confluence/download/attachments/7/bild.png";
        try {
            source().loadViewImage(foreign);
            fail();
        } catch (KnowledgeSourceException e) {
            assertEquals(Kind.UNSUPPORTED, e.kind());
            assertEquals("Bild liegt nicht unter der eingestellten Confluence-Adresse " + BASE
                    + " und wird nicht geladen: " + foreign, e.getMessage());
        }
        assertNull("kein Request an fremde Hosts", requested);
        assertEquals(1, logLines.size());
        assertTrue(logLines.get(0), logLines.get(0).contains("url=" + foreign));
        assertTrue(logLines.get(0), logLines.get(0).contains("fehler=UNSUPPORTED"));
    }

    @Test
    public void anImageAboveTheLimitSaysSo() {
        failure = new ConfluenceResponseTooLargeException(1024);

        try {
            source(1024).loadViewImage(IMAGE);
            fail();
        } catch (KnowledgeSourceException e) {
            assertEquals(Kind.INVALID_RESPONSE, e.kind());
            assertEquals("Bild größer als 1024 Bytes (Grenze maxAttachmentBytes)", e.getMessage());
        }
    }

    @Test
    public void theLogLineNamesUrlStatusTypeSizeAndKindButNoHeaders() {
        response = new ConfluenceHttpResponse(200, "application/json",
                "{\"statusCode\":200}".getBytes(StandardCharsets.UTF_8));

        failing();

        assertEquals(1, logLines.size());
        String line = logLines.get(0);
        assertTrue(line, line.contains("url=" + BASE + "/download/attachments/7/bild.png "));
        assertFalse("ohne Query", line.contains("api=v2"));
        assertTrue(line, line.contains("status=200"));
        assertTrue(line, line.contains("contentType=application/json"));
        assertTrue(line, line.contains("bytes=18"));
        assertTrue(line, line.contains("fehler=INVALID_RESPONSE"));
        assertFalse(line, line.contains("Authorization"));
        assertFalse(line, line.contains(headers.get("Authorization")));
        assertFalse(line, line.contains(secrets.principal));
        assertFalse(line, line.contains(secrets.secret));
    }

    @Test
    public void imagesUnderTheServerBaseLoadThroughTheConfiguredAddress() throws Exception {
        response = new ConfluenceHttpResponse(200, "image/png", PNG);
        ConfluenceKnowledgeSource source = source();

        PageView view = source.pageView(KnowledgeResourceId.of("confluence", "confluence.invalid/confluence/page/7"));

        String src = BASE + "/download/attachments/7/a.png?version=1&api=v2";
        assertEquals(1, view.images().size());
        assertEquals(src, view.images().get(0).src());
        assertTrue(view.html(), view.html().contains(BASE + "/download/attachments/7/a.png"));
        assertArrayEquals(PNG, source.loadViewImage(src));
        assertEquals(URI.create(src), requested);
        assertTrue(logLines.isEmpty());
    }

    @Test
    public void theSameHostInAnotherSpellingIsNotForeign() throws Exception {
        response = new ConfluenceHttpResponse(200, "image/png", PNG);

        assertArrayEquals(PNG, source().loadViewImage("https://CONFLUENCE.invalid:443/confluence/download/x.png"));

        assertEquals(URI.create(BASE + "/download/x.png"), requested);
    }

    @Test
    public void aRedirectToAnotherHostIsFollowedWithoutCredentials() throws Exception {
        response = new ConfluenceHttpResponse(302, "text/html", new byte[0],
                "https://files.invalid/blob/a.png?sig=x");
        byHost.put("files.invalid", new ConfluenceHttpResponse(200, "image/png", PNG));

        assertArrayEquals(PNG, source().loadViewImage(IMAGE));

        assertEquals(2, requests.size());
        assertEquals("files.invalid", requests.get(1).getHost());
        assertTrue(sentHeaders.get(0).containsKey("Authorization"));
        assertFalse("Anmeldung verlässt die Confluence nie", sentHeaders.get(1).containsKey("Authorization"));
        assertEquals("image/*,*/*;q=0.8", sentHeaders.get(1).get("Accept"));
    }

    @Test
    public void aRedirectToALoginPageNamesItAndLogsItsTargetWithoutQuery() {
        response = new ConfluenceHttpResponse(302, "text/html", new byte[0],
                "https://sso.invalid/login?os_destination=x");
        byHost.put("sso.invalid", new ConfluenceHttpResponse(200, "text/html", "<html>Login</html>".getBytes(
                StandardCharsets.UTF_8)));

        KnowledgeSourceException e = failing();

        assertTrue(e.getMessage(), e.getMessage().contains("text/html (vermutlich eine Anmeldeseite)"));
        assertTrue(logLines.get(0), logLines.get(0).contains("url=https://sso.invalid/login "));
        assertFalse(logLines.get(0), logLines.get(0).contains("os_destination"));
    }

    @Test
    public void aHttpsRedirectToHttpIsNotFollowed() {
        response = new ConfluenceHttpResponse(302, "text/html", new byte[0], "http://files.invalid/a.png");

        KnowledgeSourceException e = failing();

        assertEquals(Kind.ACCESS_DENIED, e.kind());
        assertEquals(1, requests.size());
        assertTrue(logLines.get(0), logLines.get(0).contains("weiterleitung=http://files.invalid/a.png "));
    }

    @Test
    public void htmlWithAnInlineSvgIsNoImageButAnSvgDocumentIs() {
        assertFalse(ConfluenceRestClient.isSvgDocument(
                "<html><body><svg></svg></body></html>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ConfluenceRestClient.isSvgDocument(("<?xml version=\"1.0\"?>\n<!-- x -->\n<!DOCTYPE svg>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\"/>").getBytes(StandardCharsets.UTF_8)));
        assertEquals("text/html (vermutlich eine Anmeldeseite)", ConfluenceRestClient.notAnImage("text/html",
                "<html><svg/></html>".getBytes(StandardCharsets.UTF_8)));
    }

    private KnowledgeSourceException failing() {
        try {
            source().loadViewImage(IMAGE);
            fail("expected KnowledgeSourceException");
            return null;
        } catch (KnowledgeSourceException e) {
            return e;
        }
    }
}
