package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Die Quellenseiten des Recherche-Laufs über die Wissenswerkzeuge des Hosts: {@code knowledge_search} findet
 * Treffer (live in den angehakten Quellen, z. B. Confluence, und im Index; der Host indexiert dabei), {@code
 * knowledge_read} liest eine Seite. Für den Lauf aus askai arch sind das die „Browser“-Werkzeuge: {@code
 * web_open(url)} liefert den Seitentext im Format des Browser-Bridges ({@code URL: … title="…" capture_id=…}),
 * {@code web_links} liefert keine Links (das Budget zählt Treffer der Suche, kein Weiterhangeln).
 */
final class KnowledgePages implements ToolInvoker,
        com.aresstack.enterpriseai.research.runtime.acquire.PagePrefetch {

    static final String SEARCH_TOOL = "knowledge_search";
    static final String READ_TOOL = "knowledge_read";
    private static final String SCHEME = "knowledge:";

    /** Ein Treffer der Wissenssuche. */
    static final class Hit {
        final String id;
        final String title;
        final String snippet;
        final String url;

        Hit(String id, String title, String snippet, String url) {
            this.id = id;
            this.title = title;
            this.snippet = snippet;
            this.url = url;
        }
    }

    private final ToolInvoker host;
    /** Der Pool fester Größe (4) für das parallele Laden der ausgewählten Treffer; {@code null}: kein Vorausladen. */
    private final ExecutorService pool;
    private final Map<String, Future<String>> prefetched = new ConcurrentHashMap<String, Future<String>>();
    /** URL des Treffers → Dokument-ID für {@code knowledge_read}. */
    private final Map<String, String> idsByUrl = new ConcurrentHashMap<String, String>();
    private final Map<String, String> titlesById = new ConcurrentHashMap<String, String>();
    private final Map<String, String> urlsById = new ConcurrentHashMap<String, String>();

    KnowledgePages(ToolInvoker host, ExecutorService pool) {
        this.host = host;
        this.pool = pool;
    }

    @Override
    public void prefetch(List<String> urls) {
        if (pool == null || urls == null) {
            return;
        }
        for (String url : urls) {
            final String id = idFor(url);
            if (prefetched.containsKey(id)) {
                continue;
            }
            prefetched.put(id, pool.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return read(id);
                }
            }));
        }
    }

    /** {@code knowledge_search}; jede Treffer-URL wird für das spätere Öffnen gemerkt. */
    List<Hit> search(String query, int limit) throws ToolFailure, EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", query);
        if (limit > 0) {
            args.put("limit", Integer.valueOf(limit));
        }
        List<Hit> hits = parseHits(host.call(SEARCH_TOOL, args));
        List<Hit> remembered = new ArrayList<Hit>();
        for (Hit hit : hits) {
            String url = hit.url.isEmpty() ? SCHEME + hit.id : hit.url;
            idsByUrl.put(url, hit.id);
            titlesById.put(captureId(hit.id), hit.title);
            urlsById.put(captureId(hit.id), url);
            remembered.add(new Hit(hit.id, hit.title, hit.snippet, url));
        }
        return remembered;
    }

    /**
     * Die Quellenübernahme des Laufs: {@code source_accept} mit Dokument-ID (die capture_id aus {@code web_open}),
     * Titel und URL, damit der Host die Quelle ohne zweiten Lesezugriff im Projekt vermerken kann.
     */
    com.aresstack.enterpriseai.research.runtime.acquire.SourceAcceptancePort acceptance() {
        return new com.aresstack.enterpriseai.research.runtime.acquire.SourceAcceptancePort() {
            @Override
            public String accept(String captureId) throws ToolFailure, EndpointUnavailable {
                Map<String, Object> args = new LinkedHashMap<String, Object>();
                args.put("capture_id", captureId);
                args.put("title", nonNull(titlesById.get(captureId)));
                args.put("url", nonNull(urlsById.get(captureId)));
                return host.call("source_accept", args);
            }

            @Override
            public void park(String url, String title, String excerpt, double rerankScore)
                    throws ToolFailure, EndpointUnavailable {
                Map<String, Object> args = new LinkedHashMap<String, Object>();
                args.put("url", nonNull(url));
                args.put("title", nonNull(title));
                args.put("excerpt", nonNull(excerpt));
                if (!Double.isNaN(rerankScore)) {
                    args.put("score", Double.toString(rerankScore));
                }
                host.call("source_park", args);
            }
        };
    }

    private String idFor(String url) {
        String trimmed = url == null ? "" : url.trim();
        String id = idsByUrl.get(trimmed);
        if (id == null) {
            id = trimmed.startsWith(SCHEME) ? trimmed.substring(SCHEME.length()) : trimmed;
        }
        return id;
    }

    private String read(String id) throws ToolFailure, EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("id", id);
        return host.call(READ_TOOL, args);
    }

    private static String await(Future<String> loaded) throws ToolFailure, EndpointUnavailable {
        try {
            return loaded.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            loaded.cancel(true);
            throw new ToolFailure("interrupted while reading");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof EndpointUnavailable) {
                throw (EndpointUnavailable) cause;
            }
            if (cause instanceof ToolFailure) {
                throw (ToolFailure) cause;
            }
            throw new ToolFailure(String.valueOf(cause));
        }
    }

    /** Die capture_id ist ein Token der Kopfzeile, also ohne Leerzeichen. */
    private static String captureId(String id) {
        return id.replace(' ', '_');
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
        if ("web_open".equals(toolName)) {
            return open(args == null ? null : String.valueOf(args.get("url")));
        }
        if ("web_links".equals(toolName)) {
            return "";
        }
        throw new ToolFailure("Not offered: " + toolName + " (Quellen kommen über " + SEARCH_TOOL + ")");
    }

    private String open(String url) throws ToolFailure, EndpointUnavailable {
        if (url == null || url.trim().isEmpty()) {
            throw new ToolFailure("Missing argument: url");
        }
        String id = idFor(url);
        String text;
        Future<String> loaded = prefetched.remove(id);
        if (loaded != null) {
            text = await(loaded);
        } else {
            text = read(id);
        }
        String title = titlesById.get(captureId(id));
        return "URL: " + url.trim() + " title=\"" + (title == null ? "" : title.replace('"', '\'')) + "\""
                + " capture_id=" + captureId(id) + "\n" + (text == null ? "" : text);
    }

    /**
     * Liest die Treffer von {@code knowledge_search}: Blöcke {@code [n] Titel – Abschnitt}, {@code id: …},
     * {@code source: …}, danach der Auszug. Tolerant auch für JSON (Array oder {@code hits}/{@code results} mit
     * {@code id}, {@code title}, {@code snippet}, {@code url}).
     */
    static List<Hit> parseHits(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return fromJson(MiniJson.parse(trimmed));
            } catch (RuntimeException notJson) {
                // fällt auf das Zeilenformat zurück
            }
        }
        List<Hit> hits = new ArrayList<Hit>();
        String title = null;
        String id = null;
        StringBuilder snippet = new StringBuilder();
        for (String line : trimmed.split("\\r?\\n")) {
            java.util.regex.Matcher header = HIT_HEADER.matcher(line);
            if (header.matches()) {
                addHit(hits, id, title, snippet);
                title = header.group(1).trim();
                id = null;
                snippet.setLength(0);
            } else if (title != null && id == null && line.startsWith("id: ")) {
                id = line.substring("id: ".length()).trim();
            } else if (title != null && id != null && !line.startsWith("source: ")
                    && !line.startsWith("Read a hit completely")) {
                if (snippet.length() > 0 && !line.trim().isEmpty()) {
                    snippet.append(' ');
                }
                snippet.append(line.trim());
            }
        }
        addHit(hits, id, title, snippet);
        return hits;
    }

    private static final java.util.regex.Pattern HIT_HEADER = java.util.regex.Pattern.compile("^\\[\\d+\\]\\s+(.*)$");

    private static void addHit(List<Hit> hits, String id, String title, StringBuilder snippet) {
        if (id != null && !id.isEmpty()) {
            hits.add(new Hit(id, title == null ? "" : title, snippet.toString().trim(), ""));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Hit> fromJson(Object parsed) {
        Object list = parsed;
        if (parsed instanceof Map) {
            Map<String, Object> object = (Map<String, Object>) parsed;
            list = object.containsKey("hits") ? object.get("hits") : object.get("results");
        }
        if (!(list instanceof List)) {
            return Collections.emptyList();
        }
        List<Hit> hits = new ArrayList<Hit>();
        for (Object element : (List<Object>) list) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<String, Object> hit = (Map<String, Object>) element;
            String id = first(hit, "id", "documentId", "resourceId");
            if (id.isEmpty()) {
                continue;
            }
            hits.add(new Hit(id, first(hit, "title", "name"), first(hit, "snippet", "excerpt", "text"),
                    first(hit, "url", "link")));
        }
        return hits;
    }

    private static String first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }
}
