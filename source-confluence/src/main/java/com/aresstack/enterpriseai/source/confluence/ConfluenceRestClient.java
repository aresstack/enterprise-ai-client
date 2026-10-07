package com.aresstack.enterpriseai.source.confluence;

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

/**
 * Confluence-Data-Center-REST-Aufrufe (paketintern). Endpunkte und Felder aus MainframeMate
 * {@code ConfluenceRestClient} übernommen; JSON über Gson statt Jackson.
 *
 * <p>HTTP-Status werden auf {@link Kind} abgebildet: 401/403 und Weiterleitungen (Login-Seite) →
 * {@code ACCESS_DENIED}, 404 → {@code NOT_FOUND}, 429/5xx und Netzfehler → {@code UNAVAILABLE}, alles andere →
 * {@code INVALID_RESPONSE}. Meldungen nennen Vorgang und Status, nie Antwortkörper oder Header.
 */
final class ConfluenceRestClient {

    private static final String PAGE_EXPAND = "body.view,version,space,ancestors,metadata.labels";
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

    /** Startseite eines Space oder {@code null}, wenn der Space nicht existiert oder keine hat. */
    Page spaceHomepage(Session session, String spaceKey) throws KnowledgeSourceException {
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
        return home == null || string(home, "id") == null ? null : page(session, string(home, "id"), false);
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

    /** CQL-Suche über {@code /rest/api/content/search}, blättert bis {@code limit} Seiten-Treffer. */
    List<Page> search(Session session, String cql, int limit) throws KnowledgeSourceException {
        return paged(session, "/rest/api/content/search?cql=" + query(cql) + "&expand=" + LIST_EXPAND, "Suche", limit,
                new Item<Page>() {
                    @Override
                    public Page map(JsonObject json) throws KnowledgeSourceException {
                        return "page".equals(string(json, "type")) ? toPage(json) : null;
                    }
                });
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
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, what + ": Confluence nicht erreichbar", e);
        }
        int status = response.status();
        if (response.isSuccess()) {
            return response;
        }
        if (status == 401 || status == 403) {
            throw new KnowledgeSourceException(Kind.ACCESS_DENIED, what + ": Zugriff verweigert (HTTP " + status + ")");
        }
        if (status >= 300 && status < 400) {
            throw new KnowledgeSourceException(Kind.ACCESS_DENIED,
                    what + ": Weiterleitung (HTTP " + status + "), vermutlich nicht angemeldet");
        }
        if (status == 404) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, what + ": nicht gefunden (HTTP 404)");
        }
        if (status == 429 || status >= 500) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, what + ": Confluence antwortet mit HTTP " + status);
        }
        throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, what + ": unerwartete Antwort HTTP " + status);
    }

    // --- JSON-Abbildung -----------------------------------------------------------------------------------

    private Page toPage(JsonObject json) throws KnowledgeSourceException {
        String id = string(json, "id");
        if (!ConfluenceIds.isContentId(id)) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, "Seite ohne gültige ID");
        }
        JsonObject space = object(json, "space");
        JsonObject version = object(json, "version");
        JsonObject body = object(object(json, "body"), "view");
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
                links == null ? null : string(links, "webui"), labels);
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

        @Override
        public String toString() {
            return "Session[***]";
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

        Page(String id, String title, String spaceKey, String version, Instant modifiedAt,
             String parentId, String html, String webUi, List<String> labels) {
            this.id = id;
            this.title = title;
            this.spaceKey = spaceKey;
            this.version = version;
            this.modifiedAt = modifiedAt;
            this.parentId = parentId;
            this.html = html;
            this.webUi = webUi;
            this.labels = Collections.unmodifiableList(labels);
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
