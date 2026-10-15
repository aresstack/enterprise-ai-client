package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeHit;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeToolOutput;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import java.util.ArrayList;
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

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(KnowledgePages.class.getName());

    static final String SEARCH_TOOL = "knowledge_search";
    static final String READ_TOOL = "knowledge_read";
    static final String RESOLVE_TOOL = "knowledge_resolve_url";
    private static final java.util.regex.Pattern WEB_ADDRESS = java.util.regex.Pattern.compile("(?i)^https?://\\S+$");
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
    /** Ob der Host nach einem Verbindungsfehler noch antwortet (dann betraf der Fehler nur den einen Treffer). */
    private final java.util.function.BooleanSupplier hostReachable;
    /** URL des Treffers → Dokument-ID für {@code knowledge_read}. */
    private final Map<String, String> idsByUrl = new ConcurrentHashMap<String, String>();
    private final Map<String, String> titlesById = new ConcurrentHashMap<String, String>();
    private final Map<String, String> urlsById = new ConcurrentHashMap<String, String>();

    KnowledgePages(ToolInvoker host) {
        this(host, reachability(host));
    }

    /** @param hostReachable fragt den Host, ob er noch antwortet (nach einem Verbindungsfehler eines Aufrufs) */
    KnowledgePages(ToolInvoker host, java.util.function.BooleanSupplier hostReachable) {
        this.host = host;
        this.hostReachable = hostReachable;
    }

    /** Versuche je Seite bei einem Verbindungsfehler (der erste und genau ein zweiter). */
    static final int READ_ATTEMPTS = 2;

    /** Fragt {@code tools/list} des Hosts; ohne Host-Steuerung (Tests mit Fakes) gilt ein Verbindungsfehler als echt. */
    static java.util.function.BooleanSupplier reachability(final ToolInvoker host) {
        if (host instanceof ResearchHostControl) {
            return new java.util.function.BooleanSupplier() {
                @Override
                public boolean getAsBoolean() {
                    return ((ResearchHostControl) host).isReachable();
                }
            };
        }
        return new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
    }

    /**
     * Liest eine Seite ({@code knowledge_read}) als Fehler genau dieses Treffers: ein Werkzeugfehler überspringt ihn,
     * ein Verbindungsfehler (Zeitüberschreitung der Quelle) bekommt genau einen zweiten Versuch, solange der Host
     * antwortet; danach wird der Treffer übersprungen. Nur ein Host, der nicht mehr antwortet, beendet den Turn.
     *
     * @return der Seitentext; {@code null}, wenn der Treffer übersprungen wird
     */
    String readPage(String id) throws EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("id", id);
        for (int attempt = 1; ; attempt++) {
            try {
                return host.call(READ_TOOL, args);
            } catch (ToolFailure unreadable) {
                LOG.info("[knowledge] Seite nicht lesbar: " + id + " " + unreadable.getMessage());
                return null;
            } catch (EndpointUnavailable lost) {
                if (!hostReachable.getAsBoolean()) {
                    throw lost;
                }
                LOG.info("[knowledge] Seite " + id + " Versuch " + attempt + " gescheitert, Host antwortet: "
                        + lost.getMessage());
                if (attempt >= READ_ATTEMPTS) {
                    return null;
                }
            }
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

    /**
     * Der Seitentext einer {@code knowledge_read}-Antwort ohne Kopfzeilen und Passagen-Marken (gemeinsames Format der
     * Runtime, {@link KnowledgeToolOutput#pageText}).
     */
    static String pageText(String readOutput) {
        return KnowledgeToolOutput.pageText(readOutput);
    }

    /**
     * Liest eine Seite des Laufs wie {@link #readPage}: eine Seite, die auch nach dem zweiten Versuch nicht kommt,
     * ist ein Fehler genau dieser Seite (der Lauf geht mit dem nächsten Treffer weiter), kein toter Endpoint.
     */
    private String read(String id) throws ToolFailure, EndpointUnavailable {
        String text = readPage(id);
        if (text == null) {
            throw new ToolFailure("Seite übersprungen: " + id);
        }
        return text;
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
        String id = idsByUrl.containsKey(url.trim()) || !WEB_ADDRESS.matcher(url.trim()).matches() ? idFor(url)
                : resolve(url.trim());
        String text = read(id);
        String title = titlesById.get(captureId(id));
        return "URL: " + url.trim() + " title=\"" + (title == null ? "" : title.replace('"', '\'')) + "\""
                + " capture_id=" + captureId(id) + "\n" + (text == null ? "" : text);
    }

    /**
     * Eine vom Benutzer genannte Webadresse ohne vorherige Suche: {@code knowledge_resolve_url} des Hosts bildet sie
     * auf die Ressource einer angebundenen Quelle ab. Genau ein Kandidat wird gelesen (und gemerkt); keiner oder
     * mehrere sind ein Fehler dieses Aufrufs, der die Kandidaten nennt, damit das Modell mit {@code knowledge:<id>}
     * gezielt öffnet — der Resolver wählt nie selbst.
     */
    private String resolve(String url) throws ToolFailure, EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("url", url);
        List<String> ids = new ArrayList<String>();
        for (String line : host.call(RESOLVE_TOOL, args).split("\\r?\\n")) {
            if (line.startsWith("id: ")) {
                ids.add(line.substring(4).trim());
            }
        }
        if (ids.isEmpty()) {
            throw new ToolFailure("Keine angebundene Wissensquelle kennt " + url
                    + " (externe Seite; Quellen kommen über " + SEARCH_TOOL + ")");
        }
        if (ids.size() > 1) {
            StringBuilder named = new StringBuilder();
            for (String candidate : ids) {
                named.append(named.length() == 0 ? "" : ", ").append(SCHEME).append(candidate);
            }
            throw new ToolFailure("Mehrere Quellen kennen " + url + ": " + named
                    + " — eine davon mit web_open öffnen");
        }
        String id = ids.get(0);
        idsByUrl.put(url, id);
        urlsById.put(captureId(id), url);
        return id;
    }

    /**
     * Liest die Treffer von {@code knowledge_search} über das gemeinsame Format der Runtime
     * ({@link KnowledgeToolOutput#parseHits}).
     */
    static List<Hit> parseHits(String text) {
        List<Hit> hits = new ArrayList<Hit>();
        for (KnowledgeHit hit : KnowledgeToolOutput.parseHits(text)) {
            hits.add(new Hit(hit.id, hit.title, hit.snippet, hit.url, hit.passage, hit.source));
        }
        return hits;
    }
}
