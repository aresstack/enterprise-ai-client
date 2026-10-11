package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Confluence-Data-Center-REST-Aufrufe (paketintern). Endpunkte und Felder aus MainframeMate
 * {@code ConfluenceRestClient} übernommen; JSON über Gson statt Jackson.
 *
 * <p>HTTP-Status werden auf {@link Kind} abgebildet: 401/403 und Weiterleitungen (Login-Seite) →
 * {@code ACCESS_DENIED}, 404 → {@code NOT_FOUND}, 429/5xx und Netzfehler → {@code UNAVAILABLE}, alles andere →
 * {@code INVALID_RESPONSE}. Meldungen nennen Vorgang und Status, nie Antwortkörper oder Header.
 */
final class ConfluenceRestClient {

    private static final Logger LOG = Logger.getLogger(ConfluenceRestClient.class.getName());

    /** {@code Accept} beim Bildrequest (statt {@code application/json} der REST-Aufrufe). */
    static final String IMAGE_ACCEPT = "image/*,*/*;q=0.8";

    private static final String PAGE_EXPAND = "body.view,version,space,ancestors,metadata.labels";
    /**
     * Für die Lese-Ansicht zusätzlich {@code body.export_view}: Confluence rendert dabei Makros, die
     * {@code body.view} nur als Lade-Platzhalter liefert und erst im Browser per Script füllt (Jira-Vorgänge und
     * Jira-Tabellen: „Getting issue details...“, nur die Kopfzeile). {@code body.view} bleibt Rückfall, falls
     * {@code export_view} leer ist; der Index liest weiter {@code body.view}.
     */
    private static final String READING_EXPAND = "body.export_view," + PAGE_EXPAND;
    /** Felder für Seiten ohne Inhalt (Startpunkte, Kindseiten, Suchtreffer); Labels sind nicht Standard. */
    private static final String LIST_EXPAND = "version,space,metadata.labels";
    /** Confluence Data Center liefert Zeitstempel auch mit kompaktem Offset ({@code +1100}), kein ISO-8601. */
    private static final DateTimeFormatter COMPACT_OFFSET =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss[.SSS]XX", Locale.ROOT);

    private final ConfluenceConfig config;
    private final ConfluenceHttpTransport transport;

    ConfluenceRestClient(ConfluenceConfig config, ConfluenceHttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    /** Der Space mit Startseiten-ID oder {@code null}, wenn es ihn nicht gibt. */
    Space space(Session session, String spaceKey) throws KnowledgeSourceException {
        JsonObject space;
        try {
            space = getJson(session, "/rest/api/space/" + pathSegment(spaceKey) + "?expand=homepage", "Space");
        } catch (KnowledgeSourceException e) {
            if (e.kind() == Kind.NOT_FOUND) {
                return null;
            }
            throw e;
        }
        JsonObject home = object(space, "homepage");
        return new Space(spaceKey, string(space, "name"), home == null ? null : string(home, "id"));
    }

    /**
     * Alle Spaces (wie MainframeMate, auch persönliche) mit Schlüssel, Namen und Startseiten-ID (MainframeMate
     * {@code SpaceItem.homepageId}), in Confluence-Reihenfolge.
     */
    List<Space> spaces(Session session, int max) throws KnowledgeSourceException {
        return paged(session, "/rest/api/space?expand=homepage", "Spaces", max, new Item<Space>() {
            @Override
            public Space map(JsonObject space) {
                String key = string(space, "key");
                if (key == null || key.trim().isEmpty()) {
                    return null;
                }
                JsonObject home = object(space, "homepage");
                return new Space(key.trim(), string(space, "name"), home == null ? null : string(home, "id"));
            }
        });
    }

    /** Alle Seiten eines Space ohne Inhalt, höchstens {@code max}, in Confluence-Reihenfolge. */
    List<Page> spacePages(Session session, String spaceKey, int max) throws KnowledgeSourceException {
        return paged(session, "/rest/api/content?type=page&spaceKey=" + query(spaceKey) + "&expand=" + LIST_EXPAND
                + ",ancestors", "Seiten des Space", max, new Item<Page>() {
                    @Override
                    public Page map(JsonObject json) throws KnowledgeSourceException {
                        return toPage(json);
                    }
                });
    }

    /**
     * @param withBody {@code true} lädt den gerenderten Inhalt ({@code body.view}) mit
     * @throws KnowledgeSourceException {@code NOT_FOUND}, wenn die Seite nicht existiert oder keine Seite ist
     */
    Page page(Session session, String contentId, boolean withBody) throws KnowledgeSourceException {
        String expand = withBody ? PAGE_EXPAND : LIST_EXPAND + ",ancestors";
        JsonObject json = getJson(session, "/rest/api/content/" + contentId + "?expand=" + expand, "Seite");
        if (!"page".equals(string(json, "type"))) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, "Inhalt " + contentId + " ist keine Seite");
        }
        return toPage(json);
    }

    /** Die Seite für die Lese-Ansicht: {@code body.export_view}, leer oder fehlend {@code body.view}. */
    Page readingPage(Session session, String contentId) throws KnowledgeSourceException {
        JsonObject json = getJson(session, "/rest/api/content/" + contentId + "?expand=" + READING_EXPAND, "Seite");
        if (!"page".equals(string(json, "type"))) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, "Inhalt " + contentId + " ist keine Seite");
        }
        return toPage(json, true);
    }

    /** Direkte Kindseiten in Confluence-Reihenfolge, höchstens {@code max}. */
    List<Page> children(Session session, String contentId, int max) throws KnowledgeSourceException {
        return paged(session, "/rest/api/content/" + contentId + "/child/page?expand=" + LIST_EXPAND, "Kindseiten",
                max, new Item<Page>() {
                    @Override
                    public Page map(JsonObject json) throws KnowledgeSourceException {
                        return toPage(json);
                    }
                });
    }

    /**
     * Anhänge einer Seite, die {@code accept} bestehen, höchstens {@code max}; es wird so lange geblättert, bis
     * {@code max} passende Anhänge beisammen sind, damit unpassende keinen Platz im Budget verbrauchen.
     */
    List<Attachment> attachments(Session session, final String contentId, int max, final Predicate<Attachment> accept)
            throws KnowledgeSourceException {
        return paged(session, "/rest/api/content/" + contentId + "/child/attachment?expand=version,container",
                "Anhänge", max, new Item<Attachment>() {
                    @Override
                    public Attachment map(JsonObject json) throws KnowledgeSourceException {
                        Attachment attachment = toAttachment(json, contentId);
                        return accept.test(attachment) ? attachment : null;
                    }
                });
    }

    /** Ein Anhang; {@code NOT_FOUND}, wenn es ihn nicht gibt. */
    Attachment attachment(Session session, String attachmentId) throws KnowledgeSourceException {
        JsonObject json = getJson(session, "/rest/api/content/" + attachmentId + "?expand=version,container",
                "Anhang");
        if (!"attachment".equals(string(json, "type"))) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, "Inhalt " + attachmentId + " ist kein Anhang");
        }
        JsonObject container = object(json, "container");
        return toAttachment(json, container == null ? null : string(container, "id"));
    }

    /** Lädt den Inhalt eines Anhangs; nur Pfade unterhalb der Basis-URL. */
    byte[] download(Session session, Attachment attachment) throws KnowledgeSourceException {
        if (attachment.downloadPath == null || !attachment.downloadPath.startsWith("/")
                || attachment.downloadPath.startsWith("//")) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                    "Anhang " + attachment.id + " ohne gültigen Download-Pfad");
        }
        ConfluenceHttpResponse response = send(session, attachment.downloadPath, "Anhang-Download",
                config.maxAttachmentBytes());
        return response.body();
    }

    /** {@code Accept} beim Laden von Anhängen und Vorschauen für den Lesebereich (wie der Browser im Mitschnitt). */
    static final String ANY_ACCEPT = "*/*";

    /**
     * Pfad der Confluence-Dokumentkonvertierung, wie Confluence ihn selbst in der Seite ({@code view-file}-Makro,
     * Vorschaubild) und im Files-Endpunkt ({@code previewContents}) nennt:
     * {@code /rest/documentConversion/latest/conversion/<art>/<numerische Anhang-ID>/<Version>} mit den Arten
     * {@code convert} (Dokument), {@code poster} und {@code thumbnail}.
     */
    static String conversionPath(String attachmentId, String version, String kind) {
        String numeric = attachmentId.startsWith("att") ? attachmentId.substring(3) : attachmentId;
        return "/rest/documentConversion/latest/conversion/" + kind + "/" + numeric + "/"
                + (version == null || version.isEmpty() ? "1" : version);
    }

    /**
     * Die Originaldatei eines Anhangs für den Lesebereich: wie {@link #download}, aber mit {@link #ANY_ACCEPT}, eigener
     * Grenze und einer Prüfung gegen Anmeldeseiten (HTML, wo der Anhang keins ist). Weiterleitungen auf einen anderen
     * Host folgt der Weg wie der Bildweg ohne Anmeldung; greift die Anmeldung nicht, gilt derselbe Sitzungs-Cookie-
     * Neuversuch wie für Bilder ({@link #withSessionCookie}).
     */
    byte[] readAttachment(Session session, final Attachment attachment, final int maxBytes)
            throws KnowledgeSourceException {
        if (attachment.downloadPath == null || !attachment.downloadPath.startsWith("/")
                || attachment.downloadPath.startsWith("//")) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                    "Anhang " + attachment.id + " ohne gültigen Download-Pfad");
        }
        return withSessionCookie(session, ANY_ACCEPT, "Anhang", new Attempt() {
            @Override
            public byte[] run(Map<String, String> headers) throws KnowledgeSourceException {
                ConfluenceHttpResponse response = fetch(headers, attachment.downloadPath, "Anhang", maxBytes);
                String received = baseMediaType(response.contentType());
                if (received.contains("html") && !baseMediaType(attachment.mediaType).contains("html")) {
                    throw fileFailure("Anhang", attachment.downloadPath, response, new KnowledgeSourceException(
                            Kind.INVALID_RESPONSE, "Anhang: erwartet " + attachment.mediaType + ", erhalten "
                            + received + " (vermutlich eine Anmeldeseite)"));
                }
                return response.body();
            }
        });
    }

    /**
     * Eine Vorschau der Dokumentkonvertierung ({@link #conversionPath}); nur PDF oder Bild gilt, alles andere (etwa
     * die Anmeldeseite) wird abgelehnt, mit Sitzungs-Cookie-Neuversuch wie beim Bild. 202 heißt: Confluence erzeugt
     * sie noch.
     */
    byte[] conversion(Session session, Attachment attachment, String kind, final int maxBytes)
            throws KnowledgeSourceException {
        final String path = conversionPath(attachment.id, attachment.version, kind);
        return withSessionCookie(session, ANY_ACCEPT, "Vorschau", new Attempt() {
            @Override
            public byte[] run(Map<String, String> headers) throws KnowledgeSourceException {
                ConfluenceHttpResponse response = fetch(headers, path, "Vorschau", maxBytes);
                if (response.status() == 202) {
                    throw new KnowledgeSourceException(Kind.UNAVAILABLE, "Vorschau wird von Confluence noch erzeugt");
                }
                byte[] body = response.body();
                if (!isPdf(body) && notAnImage(response.contentType(), body) != null) {
                    throw fileFailure("Vorschau", path, response, new KnowledgeSourceException(
                            Kind.INVALID_RESPONSE, "Vorschau: erwartet PDF oder Bild, erhalten "
                            + notAnImage(response.contentType(), body)));
                }
                return body;
            }
        });
    }

    static boolean isPdf(byte[] data) {
        return data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    /** GET mit {@link #ANY_ACCEPT}; Fehlerstatus werden mit Logzeile (ohne Query und Header) zur Ausnahme. */
    private ConfluenceHttpResponse fetch(Map<String, String> initial, String path, String what, int maxBytes)
            throws KnowledgeSourceException {
        URI uri;
        try {
            uri = URI.create(config.baseUrl() + path);
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": ungültiger Pfad");
        }
        Map<String, String> headers = initial;
        ConfluenceHttpResponse response;
        for (int hop = 0; ; hop++) {
            try {
                response = hop == 0 ? transport.get(uri, headers, maxBytes)
                        : transport.getAnonymous(uri, headers, maxBytes);
            } catch (ConfluenceResponseTooLargeException e) {
                throw fileFailure(what, uri.getRawPath(), null, new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                        what + " größer als " + maxBytes + " Bytes"));
            } catch (IOException e) {
                throw fileFailure(what, uri.getRawPath(), null, new KnowledgeSourceException(Kind.UNAVAILABLE,
                        what + ": Confluence nicht erreichbar (" + cause(e) + ")", e));
            }
            URI next = hop < 5 ? foreignRedirect(uri, response) : null;
            if (next == null) {
                break;
            }
            LOG.info("[Confluence] " + what + ": Weiterleitung von " + withoutQuery(uri.toString()) + " auf "
                    + withoutQuery(next.toString()) + ", ohne Anmeldung gefolgt");
            uri = next;
            headers = withoutAuthorization(headers);
        }
        if (!response.isSuccess()) {
            throw fileFailure(what, uri.getRawPath(), response, statusFailure(what, response.status()));
        }
        return response;
    }

    /** Logzeile zum fehlgeschlagenen Anhang bzw. zur Vorschau (Pfad ohne Query, nie Header). */
    private static KnowledgeSourceException fileFailure(String what, String path, ConfluenceHttpResponse response,
                                                        KnowledgeSourceException error) {
        LOG.warning("[Confluence] " + what + " nicht geladen: pfad=" + withoutQuery(String.valueOf(path))
                + " status=" + (response == null ? "-" : String.valueOf(response.status()))
                + " contentType=" + (response == null || response.contentType().isEmpty() ? "-"
                : response.contentType())
                + " bytes=" + (response == null ? "-" : String.valueOf(response.length()))
                + " fehler=" + error.kind() + ": " + error.getMessage());
        return error;
    }

    private static String baseMediaType(String contentType) {
        String media = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int semicolon = media.indexOf(';');
        return (semicolon < 0 ? media : media.substring(0, semicolon)).trim();
    }

    /**
     * Ein Bild der Lese-Ansicht über seinen Pfad unter der Basis-URL (wie MainframeMate {@code client.getBytes(path)},
     * dort mit {@code Accept: *}{@code /*}). Gesendet wird {@link #IMAGE_ACCEPT} statt {@code application/json}.
     * Als Bild gilt eine Antwort mit {@code image/*} oder mit Bild-Signatur im Körper (PNG, JPEG, GIF, BMP, WebP,
     * SVG), etwa bei {@code application/octet-stream}; alles andere (JSON, HTML-Anmeldeseite) wird unmittelbar nach
     * HTTP mit dem erhaltenen Content-Type abgelehnt. Jeder Fehlschlag schreibt eine Logzeile mit URL, Status,
     * Content-Type, Größe und Fehlerart, nie Header.
     */
    byte[] viewImage(Session session, String path) throws KnowledgeSourceException {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "Bild ohne gültigen Pfad");
        }
        URI uri;
        try {
            uri = URI.create(config.baseUrl() + encodeUnsafe(path));
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, "Bild: ungültiger Pfad");
        }
        final URI image = uri;
        return withSessionCookie(session, IMAGE_ACCEPT, "Bild", new Attempt() {
            @Override
            public byte[] run(Map<String, String> headers) throws KnowledgeSourceException {
                return fetchImage(image, headers);
            }
        });
    }

    /** Ein Abruf mit den gegebenen Kopfzeilen; für {@link #withSessionCookie}. */
    private interface Attempt {
        byte[] run(Map<String, String> headers) throws KnowledgeSourceException;
    }

    /**
     * Führt den Abruf angemeldet aus und wiederholt ihn einmal mit Sitzungs-Cookie, wenn die Anmeldung nicht griff
     * (Bild, Anhang, Vorschau).
     */
    private byte[] withSessionCookie(Session session, String accept, String what, Attempt attempt)
            throws KnowledgeSourceException {
        try {
            return attempt.run(session.headers(accept));
        } catch (KnowledgeSourceException first) {
            if (!needsSession(first)) {
                throw first;
            }
            // Weder MainframeMate noch dieser Client hielten eine Sitzung: Jeder Bildabruf kam nur mit dem
            // Authorization-Header. Instanzen, die Basic nur unter /rest/ annehmen (SSO), leiten /download/ ohne
            // Sitzung auf die Anmeldung um. Darum einmal das Sitzungs-Cookie eines angemeldeten REST-Aufrufs holen
            // und das Bild damit erneut laden; das Cookie lebt nur in diesem Aufruf und wird nie geloggt.
            String[] missing = new String[1];
            String cookie = sessionCookie(session, what, missing);
            if (cookie == null) {
                throw new KnowledgeSourceException(first.kind(), first.getMessage() + "; " + missing[0], first);
            }
            Map<String, String> headers = new LinkedHashMap<String, String>(session.headers(accept));
            headers.put("Cookie", cookie);
            try {
                return attempt.run(Collections.unmodifiableMap(headers));
            } catch (KnowledgeSourceException second) {
                throw new KnowledgeSourceException(second.kind(), second.getMessage()
                        + " (auch mit Sitzungs-Cookie aus der REST-Anmeldung)", second);
            }
        }
    }

    /**
     * Kodiert, was in einer URI nicht roh stehen darf (Leerzeichen, Umlaute, Anführungszeichen …), als UTF-8-Prozent-
     * kodierung; erlaubte Zeichen und vorhandene {@code %XX} bleiben. Confluence-Anhänge heißen oft mit Leerzeichen
     * (draw.io-Vorschauen „Name des Diagramms.png“); roh scheiterte {@link URI#create} („ungültiger Pfad“), und
     * MainframeMate schickte das Leerzeichen unkodiert.
     */
    static String encodeUnsafe(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c < 0x80 && (Character.isLetterOrDigit(c) || "-._~:/?#[]@!$&'()*+,;=%".indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    /** Weiterleitung, 401/403 oder HTML statt Datei: Anmeldung hat beim Download nicht gegriffen. */
    private static boolean needsSession(KnowledgeSourceException e) {
        return e.kind() == Kind.ACCESS_DENIED
                || e.kind() == Kind.INVALID_RESPONSE && e.getMessage() != null && e.getMessage().contains("html");
    }

    /**
     * Cookie-Header aus einem angemeldeten, leichten REST-Aufruf ({@code /rest/api/user/current}) oder {@code null},
     * wenn Confluence keins setzt oder der Aufruf scheitert; dann steht der Grund in {@code missing[0]}.
     */
    private String sessionCookie(Session session, String what, String[] missing) {
        URI uri = URI.create(config.baseUrl() + "/rest/api/user/current");
        ConfluenceHttpResponse response;
        try {
            response = transport.get(uri, session.headers, config.maxResponseBytes());
        } catch (IOException e) {
            LOG.warning("[Confluence] " + what + ": REST-Anmeldung für Sitzungs-Cookie fehlgeschlagen (" + cause(e) + ")");
            missing[0] = "REST-Anmeldung für das Sitzungs-Cookie nicht erreichbar (" + cause(e) + ")";
            return null;
        }
        LOG.info("[Confluence] " + what + ": REST-Anmeldung für Sitzungs-Cookie: status=" + response.status() + " cookies="
                + response.cookies().size());
        if (!response.isSuccess()) {
            missing[0] = "REST-Anmeldung für das Sitzungs-Cookie gescheitert (HTTP " + response.status() + ")";
            return null;
        }
        if (response.cookies().isEmpty()) {
            missing[0] = "Confluence setzt beim angemeldeten REST-Aufruf kein Sitzungs-Cookie";
            return null;
        }
        StringBuilder header = new StringBuilder();
        for (String cookie : response.cookies()) {
            header.append(header.length() == 0 ? "" : "; ").append(cookie);
        }
        return header.toString();
    }

    private byte[] fetchImage(URI requested, Map<String, String> initial) throws KnowledgeSourceException {
        URI uri = requested;
        Map<String, String> headers = initial;
        int max = config.maxAttachmentBytes();
        ConfluenceHttpResponse response;
        for (int hop = 0; ; hop++) {
            try {
                // nach einer Weiterleitung auf einen anderen Host: ohne Anmeldung, Cookie und Client-Zertifikat
                response = hop == 0 ? transport.get(uri, headers, max) : transport.getAnonymous(uri, headers, max);
            } catch (ConfluenceResponseTooLargeException e) {
                throw imageFailure(uri, null, new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                        "Bild größer als " + max + " Bytes (Grenze maxAttachmentBytes)"));
            } catch (IOException e) {
                throw imageFailure(uri, null, new KnowledgeSourceException(Kind.UNAVAILABLE,
                        "Bild: Confluence nicht erreichbar (" + cause(e) + ")", e));
            }
            URI next = hop < 5 ? foreignRedirect(uri, response) : null;
            if (next == null) {
                break;
            }
            // Weiterleitungen auf denselben Host folgt der Transport angemeldet; eine auf einen anderen Host (etwa
            // einen Download-Host) folgt der Bildweg wie ein Browser, aber ohne Authorization und Cookie:
            // Anmeldedaten verlassen die eingestellte Confluence nie.
            LOG.info("[Confluence] Bild: Weiterleitung von " + withoutQuery(uri.toString()) + " auf "
                    + withoutQuery(next.toString()) + ", ohne Anmeldung gefolgt");
            uri = next;
            headers = withoutAuthorization(headers);
        }
        if (!response.isSuccess()) {
            throw imageFailure(uri, response, statusFailure("Bild", response.status()));
        }
        byte[] body = response.body();
        String received = notAnImage(response.contentType(), body);
        if (received != null) {
            throw imageFailure(uri, response, new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                    "Bild: erwartet Bild, erhalten " + received + " (HTTP " + response.status() + ", "
                            + body.length + " Bytes)"));
        }
        return body;
    }

    /** Ziel einer Weiterleitung, die der Transport nicht verfolgt hat (anderer Host); nie von https auf http. */
    private static URI foreignRedirect(URI from, ConfluenceHttpResponse response) {
        int status = response.status();
        if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308
                || response.location() == null) {
            return null;
        }
        URI target;
        try {
            target = from.resolve(response.location());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = target.getScheme();
        if (target.getHost() == null || target.getRawUserInfo() != null
                || !"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(from.getScheme()) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return target;
    }

    private static Map<String, String> withoutAuthorization(Map<String, String> headers) {
        Map<String, String> copy = new LinkedHashMap<String, String>(headers);
        copy.remove("Authorization");
        copy.remove("Cookie");
        return Collections.unmodifiableMap(copy);
    }

    /** {@code null}, wenn die Antwort ein Bild ist; sonst, was stattdessen kam. */
    static String notAnImage(String contentType, byte[] body) {
        String media = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int semicolon = media.indexOf(';');
        media = (semicolon < 0 ? media : media.substring(0, semicolon)).trim();
        if (media.startsWith("image/") || looksLikeImage(body)) {
            return null;
        }
        if (media.isEmpty()) {
            return "eine Antwort ohne Content-Type";
        }
        return media.contains("html") ? media + " (vermutlich eine Anmeldeseite)" : media;
    }

    private static boolean looksLikeImage(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return true;
        }
        if (data.length > 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return true;
        }
        if (data.length > 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return true;
        }
        if (data.length > 1 && data[0] == 'B' && data[1] == 'M') {
            return true;
        }
        if (data.length > 11 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return true;
        }
        return isSvgDocument(data);
    }

    /**
     * SVG nur, wenn {@code <svg} das Wurzelelement ist: vor ihm stehen höchstens BOM, Leerraum, XML-Deklaration,
     * Kommentare und DOCTYPE. Eine HTML-Seite mit eingebettetem SVG ist kein Bild.
     */
    static boolean isSvgDocument(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 4096), java.nio.charset.StandardCharsets.UTF_8);
        int i = head.startsWith("\uFEFF") ? 1 : 0;
        while (true) {
            while (i < head.length() && Character.isWhitespace(head.charAt(i))) {
                i++;
            }
            String close = head.startsWith("<?", i) ? "?>" : head.startsWith("<!--", i) ? "-->"
                    : head.regionMatches(true, i, "<!DOCTYPE", 0, 9) ? ">" : null;
            if (close == null) {
                break;
            }
            int end = head.indexOf(close, i);
            if (end < 0) {
                return false;
            }
            i = end + close.length();
        }
        return head.startsWith("<svg", i)
                && (head.length() == i + 4 || !Character.isLetterOrDigit(head.charAt(i + 4)));
    }

    /** Schreibt die Logzeile zum fehlgeschlagenen Bild (ohne Header) und gibt den Fehler zurück. */
    private static KnowledgeSourceException imageFailure(URI uri, ConfluenceHttpResponse response,
                                                         KnowledgeSourceException error) {
        StringBuilder line = new StringBuilder("[Confluence] Bild nicht geladen: url=")
                .append(withoutQuery(uri.toString()))
                .append(" status=").append(response == null ? "-" : String.valueOf(response.status()))
                .append(" contentType=").append(response == null || response.contentType().isEmpty() ? "-"
                        : response.contentType())
                .append(" bytes=").append(response == null ? "-" : String.valueOf(response.length()));
        if (response != null && response.location() != null) {
            line.append(" weiterleitung=").append(withoutQuery(response.location()));
        }
        line.append(" fehler=").append(error.kind()).append(": ").append(error.getMessage());
        LOG.warning(line.toString());
        return error;
    }

    /** Adresse ohne Query (Logzeilen). */
    static String withoutQuery(String location) {
        int query = location.indexOf('?');
        return query < 0 ? location : location.substring(0, query);
    }

    /**
     * CQL-Suche über {@code /rest/api/content/search}, blättert bis {@code limit} Seiten-Treffer. Wie MainframeMate
     * ({@code ConfluenceSearchProvider.search()}) kommt ein Treffer entweder als Seite selbst oder als Such-Hülle
     * {@code {content: {...}, excerpt: "..."}}; der Ausschnitt der Hülle wird übernommen.
     */
    List<Page> search(Session session, String cql, int limit) throws KnowledgeSourceException {
        final int[] httpResults = new int[1];
        List<Page> pages = paged(session, "/rest/api/content/search?cql=" + query(cql) + "&expand=" + LIST_EXPAND,
                "Suche", limit, new Item<Page>() {
                    @Override
                    public Page map(JsonObject json) throws KnowledgeSourceException {
                        httpResults[0]++;
                        JsonObject content = object(json, "content");
                        if (content == null) {
                            content = json;
                        }
                        String type = string(content, "type");
                        if (type != null && !"page".equals(type)) {
                            return null;
                        }
                        return toPage(content).withExcerpt(excerpt(string(json, "excerpt")));
                    }
                });
        LOG.info("[Confluence] Suche: HTTP-Ergebnisse=" + httpResults[0] + ", erkannte Seiten=" + pages.size());
        return pages;
    }

    /** Wie MainframeMate: Markup und Hervorhebungsmarken entfernen, auf 300 Zeichen kürzen. */
    static String excerpt(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replaceAll("<[^>]+>", "").replaceAll("@@@hl@@@|@@@endhl@@@", "").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    // --- HTTP ---------------------------------------------------------------------------------------------

    /** Wandelt ein Listenelement um; {@code null} heißt: zählt nicht und wird übersprungen. */
    private interface Item<T> {
        T map(JsonObject json) throws KnowledgeSourceException;
    }

    /**
     * Blättert über einen Listen-Endpunkt, bis {@code max} verwendbare Elemente beisammen sind oder Confluence
     * keinen {@code _links.next} mehr liefert. Nur der erste Aufruf setzt {@code start}/{@code limit}; danach
     * wird der gelieferte relative Folgelink unverändert verfolgt (Data Center: relativ zum Kontextpfad, also
     * unter {@code /rest/api/}). Ein Link außerhalb davon oder ein sich wiederholender Link ist
     * {@code INVALID_RESPONSE}.
     */
    private <T> List<T> paged(Session session, String path, String what, int max, Item<T> item)
            throws KnowledgeSourceException {
        List<T> all = new ArrayList<T>();
        Set<String> visited = new HashSet<String>();
        String next = path + "&start=0&limit=" + Math.min(config.pageSize(), max);
        while (all.size() < max && next != null) {
            if (!visited.add(next)) {
                throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": Folgelink wiederholt sich");
            }
            JsonObject json = getJson(session, next, what);
            for (JsonObject result : results(json, what)) {
                T mapped = item.map(result);
                if (mapped != null) {
                    all.add(mapped);
                    if (all.size() >= max) {
                        break;
                    }
                }
            }
            next = nextLink(json, what);
        }
        return all;
    }

    private static String nextLink(JsonObject json, String what) throws KnowledgeSourceException {
        String next = string(object(json, "_links"), "next");
        if (next == null) {
            return null;
        }
        if (!next.startsWith("/rest/api/")) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": Folgelink außerhalb der REST-API");
        }
        return next;
    }

    private JsonObject getJson(Session session, String path, String what) throws KnowledgeSourceException {
        ConfluenceHttpResponse response = send(session, path, what, config.maxResponseBytes());
        if (!response.contentType().toLowerCase(java.util.Locale.ROOT).contains("json")) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE,
                    what + ": keine JSON-Antwort (Anmeldeseite eines Proxys?)");
        }
        try {
            JsonElement element = JsonParser.parseString(response.bodyAsUtf8());
            if (!element.isJsonObject()) {
                throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": JSON-Objekt erwartet");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": JSON nicht lesbar");
        }
    }

    private ConfluenceHttpResponse send(Session session, String path, String what, int maxBytes)
            throws KnowledgeSourceException {
        URI uri;
        try {
            uri = URI.create(config.baseUrl() + path);
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": ungültiger Pfad");
        }
        ConfluenceHttpResponse response;
        try {
            response = transport.get(uri, session.headers, maxBytes);
        } catch (IOException e) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, what + ": Confluence nicht erreichbar ("
                    + cause(e) + ")", e);
        }
        if (response.isSuccess()) {
            return response;
        }
        throw statusFailure(what, response.status());
    }

    /** Bildet einen Fehlerstatus auf {@link Kind} ab (siehe Klassenkommentar). */
    private static KnowledgeSourceException statusFailure(String what, int status) {
        if (status == 401 || status == 403) {
            return new KnowledgeSourceException(Kind.ACCESS_DENIED, what + ": Zugriff verweigert (HTTP " + status + ")");
        }
        if (status >= 300 && status < 400) {
            return new KnowledgeSourceException(Kind.ACCESS_DENIED,
                    what + ": Weiterleitung (HTTP " + status + "), vermutlich nicht angemeldet");
        }
        if (status == 404) {
            return new KnowledgeSourceException(Kind.NOT_FOUND, what + ": nicht gefunden (HTTP 404)");
        }
        if (status == 429 || status >= 500) {
            return new KnowledgeSourceException(Kind.UNAVAILABLE, what + ": Confluence antwortet mit HTTP " + status);
        }
        return new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": unerwartete Antwort HTTP " + status);
    }

    /** Die tiefste Ursache in einer Zeile, z. B. {@code SSLHandshakeException: Received fatal alert: bad_certificate}. */
    static String cause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.trim().isEmpty() ? ""
                : ": " + message.trim().replace('\n', ' '));
    }

    // --- JSON-Abbildung -----------------------------------------------------------------------------------

    private Page toPage(JsonObject json) throws KnowledgeSourceException {
        return toPage(json, false);
    }

    private Page toPage(JsonObject json, boolean exportView) throws KnowledgeSourceException {
        String id = string(json, "id");
        if (!ConfluenceIds.isContentId(id)) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, "Seite ohne gültige ID");
        }
        JsonObject space = object(json, "space");
        JsonObject version = object(json, "version");
        JsonObject bodies = object(json, "body");
        JsonObject body = exportView ? object(bodies, "export_view") : null;
        if (body == null || string(body, "value") == null || string(body, "value").trim().isEmpty()) {
            body = object(bodies, "view");
        }
        List<String> ancestors = new ArrayList<String>();
        JsonArray ancestorArray = array(json, "ancestors");
        if (ancestorArray != null) {
            for (JsonElement ancestor : ancestorArray) {
                if (ancestor.isJsonObject() && string(ancestor.getAsJsonObject(), "id") != null) {
                    ancestors.add(string(ancestor.getAsJsonObject(), "id"));
                }
            }
        }
        List<String> labels = new ArrayList<String>();
        JsonObject labelPage = object(object(json, "metadata"), "labels");
        JsonArray labelArray = array(labelPage, "results"); // optional, fehlt ohne expand=metadata.labels
        for (JsonElement label : labelArray == null ? new JsonArray() : labelArray) {
            if (label.isJsonObject() && string(label.getAsJsonObject(), "name") != null) {
                labels.add(string(label.getAsJsonObject(), "name"));
            }
        }
        JsonObject links = object(json, "_links");
        return new Page(id, nullToEmpty(string(json, "title")),
                space == null ? null : string(space, "key"),
                version == null ? null : string(version, "number"), version == null ? null : instant(string(version, "when")),
                ancestors.isEmpty() ? null : ancestors.get(ancestors.size() - 1),
                body == null ? null : string(body, "value"),
                links == null ? null : string(links, "webui"), labels)
                .withServerBase(links == null ? null : string(links, "base"));
    }

    private static Attachment toAttachment(JsonObject json, String containerId) throws KnowledgeSourceException {
        String id = string(json, "id");
        if (!ConfluenceIds.isAttachmentId(id)) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, "Anhang ohne gültige ID");
        }
        JsonObject version = object(json, "version");
        JsonObject metadata = object(json, "metadata");
        JsonObject extensions = object(json, "extensions");
        JsonObject links = object(json, "_links");
        String mediaType = metadata == null ? null : string(metadata, "mediaType");
        if (mediaType == null && extensions != null) {
            mediaType = string(extensions, "mediaType");
        }
        long size = -1;
        if (extensions != null && extensions.has("fileSize")) {
            try {
                size = extensions.get("fileSize").getAsLong();
            } catch (RuntimeException e) {
                size = -1;
            }
        }
        return new Attachment(id, nullToEmpty(string(json, "title")), containerId,
                mediaType == null ? "application/octet-stream" : mediaType, size,
                version == null ? null : string(version, "number"), version == null ? null : instant(string(version, "when")),
                links == null ? null : string(links, "download"), links == null ? null : string(links, "webui"));
    }

    /** {@code results} eines Listen-Endpunkts; fehlt es, ist die Antwort fehlerhaft, nicht leer. */
    private static List<JsonObject> results(JsonObject json, String what) throws KnowledgeSourceException {
        JsonArray array = array(json, "results");
        if (array == null) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": Liste ohne results");
        }
        List<JsonObject> list = new ArrayList<JsonObject>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                list.add(element.getAsJsonObject());
            }
        }
        return list;
    }

    private static JsonObject object(JsonObject json, String key) {
        if (json == null) {
            return null;
        }
        JsonElement value = json.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Instant instant(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException iso) {
            try {
                return OffsetDateTime.parse(value, COMPACT_OFFSET).toInstant();
            } catch (DateTimeParseException compact) {
                return null;
            }
        }
    }

    private static String query(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pathSegment(String value) {
        return query(value);
    }

    // --- Werte --------------------------------------------------------------------------------------------

    /**
     * Header eines Vorgangs (Accept und ggf. Authorization). Lebt nur für einen Port-Aufruf im Stack des
     * Aufrufers und wird nie in Feldern gehalten, geloggt oder ausgegeben.
     */
    static final class Session {

        private final Map<String, String> headers;

        Session(String authorization) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            map.put("Accept", "application/json");
            if (authorization != null) {
                map.put("Authorization", authorization);
            }
            this.headers = Collections.unmodifiableMap(map);
        }

        /** Dieselben Header mit anderem {@code Accept} (Bilder statt JSON). */
        Map<String, String> headers(String accept) {
            Map<String, String> map = new LinkedHashMap<String, String>(headers);
            map.put("Accept", accept);
            return Collections.unmodifiableMap(map);
        }

        @Override
        public String toString() {
            return "Session[***]";
        }
    }

    static final class Space {
        final String key;
        final String name;
        /** Content-ID der Startseite oder {@code null}. */
        final String homepageId;

        Space(String key, String name, String homepageId) {
            this.key = key;
            this.name = name;
            this.homepageId = homepageId;
        }
    }

    static final class Page {
        final String id;
        final String title;
        final String spaceKey;
        final String version;
        final Instant modifiedAt;
        final String parentId;
        final String html;
        final String webUi;
        final List<String> labels;
        /** Klartext-Ausschnitt aus der Suche; leer außerhalb der Suche. */
        final String excerpt;
        /**
         * {@code _links.base} der Antwort: die Basis-URL, die Confluence selbst für sich eingestellt hat und mit der
         * {@code export_view} absolute Bildadressen schreibt; {@code null}, wenn sie fehlt.
         */
        final String serverBase;

        Page(String id, String title, String spaceKey, String version, Instant modifiedAt,
             String parentId, String html, String webUi, List<String> labels) {
            this(id, title, spaceKey, version, modifiedAt, parentId, html, webUi, labels, "", null);
        }

        private Page(String id, String title, String spaceKey, String version, Instant modifiedAt,
                     String parentId, String html, String webUi, List<String> labels, String excerpt,
                     String serverBase) {
            this.id = id;
            this.title = title;
            this.spaceKey = spaceKey;
            this.version = version;
            this.modifiedAt = modifiedAt;
            this.parentId = parentId;
            this.html = html;
            this.webUi = webUi;
            this.labels = Collections.unmodifiableList(labels);
            this.excerpt = excerpt;
            this.serverBase = serverBase;
        }

        Page withExcerpt(String excerpt) {
            return new Page(id, title, spaceKey, version, modifiedAt, parentId, html, webUi, labels, excerpt,
                    serverBase);
        }

        Page withServerBase(String base) {
            return new Page(id, title, spaceKey, version, modifiedAt, parentId, html, webUi, labels, excerpt, base);
        }
    }

    static final class Attachment {
        final String id;
        final String title;
        final String containerId;
        final String mediaType;
        final long size;
        final String version;
        final Instant modifiedAt;
        final String downloadPath;
        final String webUi;

        Attachment(String id, String title, String containerId, String mediaType, long size, String version,
                   Instant modifiedAt, String downloadPath, String webUi) {
            this.id = id;
            this.title = title;
            this.containerId = containerId;
            this.mediaType = mediaType;
            this.size = size;
            this.version = version;
            this.modifiedAt = modifiedAt;
            this.downloadPath = downloadPath;
            this.webUi = webUi;
        }
    }
}
