package com.aresstack.enterpriseai.source.confluence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-Memory-Confluence hinter {@link ConfluenceHttpTransport}: beantwortet die REST-Endpunkte, die der Adapter
 * benutzt, mit JSON in der Form von Confluence Data Center und protokolliert alle Requests.
 */
final class FakeConfluence implements ConfluenceHttpTransport {

    static final URI BASE = URI.create("https://confluence.example.org/wiki");

    final Map<String, FakePage> pages = new LinkedHashMap<String, FakePage>();
    final Map<String, String> spaceHomepages = new LinkedHashMap<String, String>();
    final Map<String, FakeAttachment> attachments = new LinkedHashMap<String, FakeAttachment>();
    final List<URI> requests = new ArrayList<URI>();
    final List<Map<String, String>> requestHeaders = new ArrayList<Map<String, String>>();
    /** Ergebnis-IDs der nächsten CQL-Suche; die letzte gesendete CQL steht in {@link #lastCql}. */
    final List<String> searchResults = new ArrayList<String>();
    String lastCql;
    /** Erzwingt eine feste Antwort für alle Requests (Fehlerfälle). */
    ConfluenceHttpResponse forcedResponse;
    IOException forcedFailure;
    /** Liefert statt des echten Folgelinks einen Link außerhalb der REST-API. */
    String nextLinkOverride;

    FakePage page(String id, String title, String spaceKey, String html) {
        FakePage page = new FakePage(id, title, spaceKey, html);
        pages.put(id, page);
        return page;
    }

    void child(String parentId, String childId) {
        pages.get(parentId).children.add(childId);
        pages.get(childId).parentId = parentId;
    }

    FakeAttachment attachment(String id, String pageId, String title, String mediaType, String content) {
        FakeAttachment attachment = new FakeAttachment(id, pageId, title, mediaType,
                content.getBytes(StandardCharsets.UTF_8));
        attachments.put(id, attachment);
        pages.get(pageId).attachments.add(id);
        return attachment;
    }

    int requestCount(String pathPart) {
        int count = 0;
        for (URI uri : requests) {
            if (uri.toString().contains(pathPart)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public ConfluenceHttpResponse get(URI uri, Map<String, String> headers, int maxBytes) throws IOException {
        requests.add(uri);
        requestHeaders.add(headers);
        if (forcedFailure != null) {
            throw forcedFailure;
        }
        if (forcedResponse != null) {
            return forcedResponse;
        }
        String base = BASE.getPath();
        String path = uri.getRawPath();
        if (!uri.getHost().equals(BASE.getHost()) || !path.startsWith(base + "/")) {
            return notFound();
        }
        path = path.substring(base.length());
        Map<String, String> query = query(uri.getRawQuery());
        String[] parts = path.split("/");
        if (path.startsWith("/rest/api/space/") && parts.length == 5) {
            String key = decode(parts[4]);
            if (!spaceHomepages.containsKey(key)) {
                return notFound();
            }
            JsonObject space = new JsonObject();
            space.addProperty("key", key);
            JsonObject home = new JsonObject();
            home.addProperty("id", spaceHomepages.get(key));
            space.add("homepage", home);
            return json(space);
        }
        if (path.equals("/rest/api/content/search")) {
            lastCql = query.get("cql");
            int start = Integer.parseInt(query.getOrDefault("start", "0"));
            int limit = Integer.parseInt(query.get("limit"));
            JsonArray results = new JsonArray();
            for (int i = start; i < searchResults.size() && results.size() < limit; i++) {
                results.add(pageJson(pages.get(searchResults.get(i)), query.get("expand")));
            }
            return json(page(results, nextLink(uri, start + results.size(), searchResults.size())));
        }
        if (path.startsWith("/rest/api/content/") && parts.length == 5) {
            String id = parts[4];
            if (pages.containsKey(id)) {
                return json(pageJson(pages.get(id), query.get("expand")));
            }
            if (attachments.containsKey(id)) {
                return json(attachmentJson(attachments.get(id)));
            }
            return notFound();
        }
        if (path.startsWith("/rest/api/content/") && parts.length == 7 && "child".equals(parts[5])) {
            FakePage parent = pages.get(parts[4]);
            if (parent == null) {
                return notFound();
            }
            List<String> ids = "page".equals(parts[6]) ? parent.children : parent.attachments;
            int start = Integer.parseInt(query.get("start"));
            int limit = Integer.parseInt(query.get("limit"));
            JsonArray results = new JsonArray();
            for (int i = start; i < ids.size() && results.size() < limit; i++) {
                results.add("page".equals(parts[6]) ? pageJson(pages.get(ids.get(i)), query.get("expand"))
                        : attachmentJson(attachments.get(ids.get(i))));
            }
            return json(page(results, nextLink(uri, start + results.size(), ids.size())));
        }
        if (path.startsWith("/download/attachments/")) {
            for (FakeAttachment attachment : attachments.values()) {
                if (path.equals(attachment.downloadPath().split("\\?")[0])) {
                    if (attachment.content.length > maxBytes) {
                        throw new IOException("zu groß");
                    }
                    return new ConfluenceHttpResponse(200, attachment.mediaType, attachment.content);
                }
            }
        }
        return notFound();
    }

    /** Folgelink wie Confluence Data Center: relativ zum Kontextpfad, Query unverändert bis auf {@code start}. */
    private String nextLink(URI uri, int nextStart, int total) {
        if (nextStart >= total) {
            return null;
        }
        if (nextLinkOverride != null) {
            return nextLinkOverride;
        }
        return uri.getRawPath().substring(BASE.getPath().length()) + "?"
                + uri.getRawQuery().replaceAll("start=\\d+", "start=" + nextStart);
    }

    private static JsonObject page(JsonArray results, String next) {
        JsonObject page = new JsonObject();
        page.add("results", results);
        page.addProperty("size", results.size());
        JsonObject links = new JsonObject();
        if (next != null) {
            links.addProperty("next", next);
        }
        page.add("_links", links);
        return page;
    }

    private JsonObject pageJson(FakePage page, String expand) {
        boolean withBody = expand != null && expand.contains("body.view");
        boolean withLabels = expand != null && expand.contains("metadata.labels");
        JsonObject json = new JsonObject();
        json.addProperty("id", page.id);
        json.addProperty("type", "page");
        json.addProperty("title", page.title);
        JsonObject space = new JsonObject();
        space.addProperty("key", page.spaceKey);
        space.addProperty("name", page.spaceKey + " Space");
        json.add("space", space);
        JsonObject version = new JsonObject();
        version.addProperty("number", page.version);
        version.addProperty("when", page.when);
        json.add("version", version);
        JsonArray ancestors = new JsonArray();
        if (page.parentId != null) {
            JsonObject ancestor = new JsonObject();
            ancestor.addProperty("id", page.parentId);
            ancestors.add(ancestor);
        }
        json.add("ancestors", ancestors);
        if (withBody) {
            JsonObject view = new JsonObject();
            view.addProperty("value", page.html);
            view.addProperty("representation", "view");
            JsonObject body = new JsonObject();
            body.add("view", view);
            json.add("body", body);
        }
        if (withLabels) {
            JsonArray labels = new JsonArray();
            for (String name : page.labels) {
                JsonObject label = new JsonObject();
                label.addProperty("name", name);
                labels.add(label);
            }
            JsonObject labelPage = new JsonObject();
            labelPage.add("results", labels);
            JsonObject metadata = new JsonObject();
            metadata.add("labels", labelPage);
            json.add("metadata", metadata);
        }
        JsonObject links = new JsonObject();
        links.addProperty("webui", "/pages/viewpage.action?pageId=" + page.id);
        json.add("_links", links);
        return json;
    }

    private JsonObject attachmentJson(FakeAttachment attachment) {
        JsonObject json = new JsonObject();
        json.addProperty("id", attachment.id);
        json.addProperty("type", "attachment");
        json.addProperty("title", attachment.title);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("mediaType", attachment.mediaType);
        json.add("metadata", metadata);
        JsonObject extensions = new JsonObject();
        extensions.addProperty("mediaType", attachment.mediaType);
        extensions.addProperty("fileSize", attachment.content.length);
        json.add("extensions", extensions);
        JsonObject version = new JsonObject();
        version.addProperty("number", 1);
        version.addProperty("when", "2026-10-02T10:00:00.000Z");
        json.add("version", version);
        JsonObject container = new JsonObject();
        container.addProperty("id", attachment.pageId);
        container.addProperty("type", "page");
        json.add("container", container);
        JsonObject links = new JsonObject();
        links.addProperty("download", attachment.downloadOverride != null
                ? attachment.downloadOverride : attachment.downloadPath());
        links.addProperty("webui", "/pages/viewpageattachments.action?pageId=" + attachment.pageId);
        json.add("_links", links);
        return json;
    }

    private static ConfluenceHttpResponse json(JsonObject json) {
        return new ConfluenceHttpResponse(200, "application/json;charset=UTF-8",
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static ConfluenceHttpResponse notFound() {
        return new ConfluenceHttpResponse(404, "application/json",
                "{\"statusCode\":404,\"message\":\"No content found\"}".getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            map.put(decode(eq < 0 ? pair : pair.substring(0, eq)), eq < 0 ? "" : decode(pair.substring(eq + 1)));
        }
        return map;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class FakePage {
        final String id;
        final String title;
        final String spaceKey;
        final String html;
        final List<String> children = new ArrayList<String>();
        final List<String> attachments = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        String parentId;
        int version = 3;
        String when = "2026-10-01T08:15:00.000+02:00";

        FakePage(String id, String title, String spaceKey, String html) {
            this.id = id;
            this.title = title;
            this.spaceKey = spaceKey;
            this.html = html;
        }

        FakePage labels(String... names) {
            Collections.addAll(labels, names);
            return this;
        }
    }

    static final class FakeAttachment {
        final String id;
        final String pageId;
        final String title;
        final String mediaType;
        final byte[] content;
        String downloadOverride;

        FakeAttachment(String id, String pageId, String title, String mediaType, byte[] content) {
            this.id = id;
            this.pageId = pageId;
            this.title = title;
            this.mediaType = mediaType;
            this.content = content;
        }

        String downloadPath() {
            return "/download/attachments/" + pageId + "/" + title + "?version=1&api=v2";
        }
    }
}
