package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Quellenseiten des Recherche-Laufs über die Wissenswerkzeuge des Hosts: {@code knowledge_search} findet
 * Treffer (live in den angehakten Quellen, z. B. Confluence, und im Index; der Host indexiert dabei), {@code
 * knowledge_read} liest eine Seite. Für den Lauf aus askai arch sind das die „Browser“-Werkzeuge: {@code
 * web_open(url)} liefert den Seitentext im Format des Browser-Bridges ({@code URL: … title="…" capture_id=…}),
 * {@code web_links} liefert keine Links (das Budget zählt Treffer der Suche, kein Weiterhangeln).
 */
final class KnowledgePages implements ToolInvoker {

    static final String SEARCH_TOOL = "knowledge_search";
    static final String READ_TOOL = "knowledge_read";
    private static final String SCHEME = "knowledge:";

    /**
     * Ein Treffer der Wissenssuche. {@code id} ist die opake Ressourcen-ID für {@code knowledge_read}, {@code url}
     * die echte Adresse zum Öffnen und Zitieren (leer, wenn die Quelle keine hat), {@code passage} die Passage-ID
     * und {@code source} die Quelle — alles Metadaten, nie Teil des Auszugs.
     */
    static final class Hit {
        final String id;
        final String title;
        final String snippet;
        final String url;
        final String passage;
        final String source;

        Hit(String id, String title, String snippet, String url) {
            this(id, title, snippet, url, "", "");
        }

        Hit(String id, String title, String snippet, String url, String passage, String source) {
            this.id = id;
            this.title = title;
            this.snippet = snippet;
            this.url = url;
            this.passage = passage;
            this.source = source;
        }
    }

    private final ToolInvoker host;
    /** URL des Treffers → Dokument-ID für {@code knowledge_read}. */
    private final Map<String, String> idsByUrl = new ConcurrentHashMap<String, String>();
    private final Map<String, String> titlesById = new ConcurrentHashMap<String, String>();
    private final Map<String, String> urlsById = new ConcurrentHashMap<String, String>();

    KnowledgePages(ToolInvoker host) {
        this.host = host;
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
            remembered.add(new Hit(hit.id, hit.title, hit.snippet, url, hit.passage, hit.source));
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
        String text = read(id);
        String title = titlesById.get(captureId(id));
        return "URL: " + url.trim() + " title=\"" + (title == null ? "" : title.replace('"', '\'')) + "\""
                + " capture_id=" + captureId(id) + "\n" + (text == null ? "" : text);
    }

    /**
     * Liest die Treffer von {@code knowledge_search}: Blöcke {@code [n] Titel – Abschnitt}, direkt darunter die
     * Metadatenzeilen {@code id: …}, {@code url: …}, {@code passage: …}, {@code source: …} (nur {@code id:} ist
     * Pflicht), danach der Auszug. Metadaten gehen nie in den Auszug. Tolerant auch für JSON (Array oder {@code
     * hits}/{@code results} mit {@code id}, {@code title}, {@code snippet}, {@code url}).
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
        HitLines current = null;
        for (String line : trimmed.split("\\r?\\n")) {
            java.util.regex.Matcher header = HIT_HEADER.matcher(line);
            if (header.matches()) {
                addHit(hits, current);
                current = new HitLines(header.group(1).trim());
            } else if (current != null) {
                current.accept(line);
            }
        }
        addHit(hits, current);
        return hits;
    }

    private static final java.util.regex.Pattern HIT_HEADER = java.util.regex.Pattern.compile("^\\[\\d+\\]\\s+(.*)$");

    /** Die Zeilen eines Treffers: erst der Metadatenkopf, ab der ersten anderen Zeile der Auszug. */
    private static final class HitLines {
        final String title;
        String id = "";
        String url = "";
        String passage = "";
        String source = "";
        boolean inSnippet;
        final StringBuilder snippet = new StringBuilder();

        HitLines(String title) {
            this.title = title;
        }

        void accept(String line) {
            if (line.startsWith("Read a hit completely")) {
                return;
            }
            if (!inSnippet) {
                if (line.startsWith("id: ")) {
                    id = value(line, "id: ");
                    return;
                }
                if (line.startsWith("url: ")) {
                    url = value(line, "url: ");
                    return;
                }
                if (line.startsWith("passage: ")) {
                    passage = value(line, "passage: ");
                    return;
                }
                if (line.startsWith("source: ")) {
                    source = value(line, "source: ");
                    return;
                }
                if (id.isEmpty() || line.trim().isEmpty()) {
                    return;
                }
                inSnippet = true;
            }
            if (line.trim().isEmpty()) {
                return;
            }
            if (snippet.length() > 0) {
                snippet.append(' ');
            }
            snippet.append(line.trim());
        }

        private static String value(String line, String key) {
            return line.substring(key.length()).trim();
        }
    }

    private static void addHit(List<Hit> hits, HitLines lines) {
        if (lines != null && !lines.id.isEmpty()) {
            hits.add(new Hit(lines.id, lines.title, lines.snippet.toString(), lines.url, lines.passage,
                    lines.source));
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
                    first(hit, "url", "link"), first(hit, "passage", "passageId"), first(hit, "source", "sourceId")));
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
