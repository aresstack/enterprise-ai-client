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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String expand = withBody ? PAGE_EXPAND : "version,space,ancestors";
        JsonObject json = getJson(session, "/rest/api/content/" + contentId + "?expand=" + expand, "Seite");
        if (!"page".equals(string(json, "type"))) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, "Inhalt " + contentId + " ist keine Seite");
        }
        return toPage(json);
    }

    /** Direkte Kindseiten in Confluence-Reihenfolge, höchstens {@code max}. */
    List<Page> children(Session session, String contentId, int max) throws KnowledgeSourceException {
        List<Page> pages = new ArrayList<Page>();
        for (JsonObject json : paged(session, "/rest/api/content/" + contentId + "/child/page?expand=version,space",
                "Kindseiten", max)) {
            pages.add(toPage(json));
        }
        return pages;
    }

    /** Anhänge einer Seite, höchstens {@code max}. */
    List<Attachment> attachments(Session session, String contentId, int max) throws KnowledgeSourceException {
        List<Attachment> attachments = new ArrayList<Attachment>();
        for (JsonObject json : paged(session,
                "/rest/api/content/" + contentId + "/child/attachment?expand=version,container", "Anhänge", max)) {
            attachments.add(toAttachment(json, contentId));
        }
        return attachments;
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

    /** CQL-Suche über {@code /rest/api/content/search}, höchstens {@code limit} Treffer. */
    List<Page> search(Session session, String cql, int limit) throws KnowledgeSourceException {
        List<Page> pages = new ArrayList<Page>();
        JsonObject json = getJson(session, "/rest/api/content/search?cql=" + query(cql) + "&limit=" + limit
                + "&expand=version,space", "Suche");
        for (JsonObject result : results(json)) {
            if ("page".equals(string(result, "type")) && pages.size() < limit) {
                pages.add(toPage(result));
            }
        }
        return pages;
    }

    // --- HTTP ---------------------------------------------------------------------------------------------

    private List<JsonObject> paged(Session session, String path, String what, int max)
            throws KnowledgeSourceException {
        List<JsonObject> all = new ArrayList<JsonObject>();
        int start = 0;
        while (all.size() < max) {
            int limit = Math.min(config.pageSize(), max - all.size());
            JsonObject json = getJson(session, path + "&start=" + start + "&limit=" + limit, what);
            List<JsonObject> results = results(json);
            all.addAll(results.subList(0, Math.min(results.size(), max - all.size())));
            JsonObject links = object(json, "_links");
            if (results.isEmpty() || links == null || string(links, "next") == null) {
                break;
            }
            start += results.size();
        }
        return all;
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
        for (JsonObject label : labelPage == null ? Collections.<JsonObject>emptyList() : results(labelPage)) {
            if (string(label, "name") != null) {
                labels.add(string(label, "name"));
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

    private static List<JsonObject> results(JsonObject json) {
        JsonArray array = array(json, "results");
        if (array == null) {
            return Collections.emptyList();
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
        } catch (DateTimeParseException e) {
            return null;
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
