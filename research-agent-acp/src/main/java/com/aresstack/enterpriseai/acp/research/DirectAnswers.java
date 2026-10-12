package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.acquire.SearchScopeControlPort;
import com.aresstack.enterpriseai.research.runtime.acquire.ServiceSearchScopeControlPort;
import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Der direkte Suchauftrag ohne Konzeptpflicht: eine Frage im Agent-Modus wird sofort in den aktivierten
 * Wissensquellen gesucht ({@code knowledge_search}), die besten Seiten werden gelesen ({@code knowledge_read}), als
 * Quellen des Projekts notiert ({@code project_source_add}; Indexieren und Aufbereiten übernimmt der Host) und die
 * Antwort entsteht inhaltlich aus ihnen, jede Aussage mit Quellennummer. Gibt es schon ein Konzept, prägt es die
 * Suchanfragen; der Weidezaun filtert die Treffer ({@code search_scope_*}).
 */
final class DirectAnswers {

    private static final Logger LOG = Logger.getLogger(DirectAnswers.class.getName());
    static final int MAX_QUERIES = 4;
    static final int HITS_PER_QUERY = 6;
    /** Seitenbudget der interaktiven Recherche in der Konzeptphase. */
    static final int MAX_PAGES = 4;
    private static final int PROJECT_PASSAGES = 8;
    private static final int PAGE_EXCERPT_CHARS = 3000;
    private static final int OUTPUT_TOKENS = 3000;

    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
            "aber", "alle", "als", "auch", "auf", "aus", "bei", "bitte", "das", "dass", "dem", "den", "der", "des",
            "die", "dir", "doch", "du", "ein", "eine", "einen", "einer", "etwas", "finde", "finden", "für", "gibt",
            "hat", "ich", "ist", "kannst", "mal", "mehr", "mir", "mit", "nach", "noch", "oder", "sich", "sind",
            "über", "und", "uns", "von", "was", "welche", "welcher", "wer", "wie", "wir", "wo", "zu", "zum", "zur",
            "confluence", "wiki", "infos", "informationen", "information", "suche", "such", "genau", "the", "and",
            "what", "about", "find"));

    private final ToolInvoker host;
    private final MainModelChat model;
    private final KnowledgePages pages;
    /** Ob die letzte Recherche gesucht und gelesen hat (für Haken oder Retry eines gelben Vorschlags). */
    private volatile boolean lastSucceeded;

    DirectAnswers(ToolInvoker host, MainModelChat model) {
        this(host, model, KnowledgePages.reachability(host));
    }

    /** @param hostReachable fragt den Host, ob er noch antwortet (nach einem Verbindungsfehler eines Aufrufs) */
    DirectAnswers(ToolInvoker host, MainModelChat model, java.util.function.BooleanSupplier hostReachable) {
        this.host = host;
        this.model = model;
        this.pages = new KnowledgePages(host, hostReachable);
    }

    /**
     * Liest eine Seite ({@code knowledge_read}) als Fehler genau dieses Treffers ({@link KnowledgePages#readPage}),
     * als reiner Seitentext ohne die Kopfzeilen des Werkzeugs ({@link KnowledgePages#pageText}).
     *
     * @return der Seitentext; {@code null}, wenn der Treffer übersprungen wird
     */
    String readPage(String id) throws ToolInvoker.EndpointUnavailable {
        String page = pages.readPage(id);
        return page == null ? null : KnowledgePages.pageText(page);
    }

    /** Versuche je Seite bei einem Verbindungsfehler (der erste und genau ein zweiter). */
    static final int READ_ATTEMPTS = KnowledgePages.READ_ATTEMPTS;

    /**
     * Ob die Nachricht Wissen aus den Quellen braucht, und wenn ja, mit welchen Suchanfragen. Entscheidet das Modell
     * (JSON {@code {"search":…, "queries":[…]}}); liefert es nichts Brauchbares, sucht der Agent mit den
     * Schlüsselwörtern der Nachricht.
     *
     * @return die Suchanfragen, leer, wenn nicht gesucht werden soll
     */
    List<String> plan(String message, String concept, String fence) {
        String system = "Du bist der Recherche-Agent eines Unternehmens-Clients und kannst in den aktivierten "
                + "Wissensquellen des Unternehmens (z. B. Confluence) suchen und Seiten lesen. Entscheide, ob die "
                + "Nachricht des Nutzers Informationen aus diesen Quellen braucht. Antworte NUR mit JSON: "
                + "{\"search\": true oder false, \"queries\": [\"...\"]}. search ist false NUR, wenn die Nachricht "
                + "ausschließlich das Konzept bearbeitet (Karten anlegen, verschieben, umbenennen, ausschließen) oder "
                + "reiner Smalltalk ist. queries: 1 bis " + MAX_QUERIES + " kurze Suchanfragen mit 1 bis 3 Wörtern "
                + "(Fachbegriffe, Abkürzungen, Eigennamen; keine Füllwörter, keine Fragen), denn die Volltextsuche "
                + "verknüpft alle Wörter mit UND. Die erste Anfrage ist der Kernbegriff allein.";
        StringBuilder user = new StringBuilder("NACHRICHT: ").append(message.trim()).append('\n');
        if (concept != null && !concept.trim().isEmpty()) {
            user.append("\nKONZEPT (Themen des Auftrags):\n").append(concept.trim()).append('\n');
        }
        if (fence != null && !fence.trim().isEmpty()) {
            user.append("\nWEIDEZAUN (Ausgeschlossenes nie suchen):\n").append(fence.trim()).append('\n');
        }
        String answer = complete(system, user.toString(), 0.0d, 300);
        List<String> parsed = parsePlan(answer);
        if (parsed != null && (!parsed.isEmpty() || !asksForSearch(message))) {
            return parsed;
        }
        if (parsed != null) {
            LOG.info("[direct] ausdrücklicher Suchauftrag, das Modell wollte nicht suchen; Schlüsselwörter");
            return keywordQueries(message);
        }
        LOG.info("[direct] kein auswertbarer Suchplan vom Modell; Schlüsselwörter der Nachricht");
        return keywordQueries(message);
    }

    private static final java.util.regex.Pattern SEARCH_REQUEST = java.util.regex.Pattern.compile(
            "\\b(such\\w*|recherch\\w*|finde\\w*|find|search\\w*|nachschlagen|schau\\w* nach|look up)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /**
     * Ein ausdrücklicher Suchauftrag („Suche in Confluence nach …“, „recherchiere …“, „finde Informationen zu …“):
     * dann wird immer gesucht, auch wenn das Modell es anders sieht.
     */
    static boolean asksForSearch(String message) {
        return message != null && SEARCH_REQUEST.matcher(message).find();
    }

    /** {@code null}, wenn die Antwort kein Plan ist; leer bei {@code search=false}. */
    static List<String> parsePlan(String text) {
        String json = ResearchWriting.clean(text);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(json.substring(start, end + 1));
        } catch (RuntimeException invalid) {
            return null;
        }
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<?, ?> plan = (Map<?, ?>) parsed;
        Object search = plan.get("search");
        if (Boolean.FALSE.equals(search) || "false".equals(String.valueOf(search))) {
            return new ArrayList<String>();
        }
        Set<String> queries = new LinkedHashSet<String>();
        if (plan.get("queries") instanceof List) {
            for (Object query : (List<?>) plan.get("queries")) {
                String value = query == null ? "" : query.toString().trim();
                if (!value.isEmpty() && queries.size() < MAX_QUERIES) {
                    queries.add(value);
                }
            }
        }
        return queries.isEmpty() ? null : new ArrayList<String>(queries);
    }

    /** Ersatzplan ohne Modell: die ersten Schlüsselwörter der Nachricht, einzeln und zusammen. */
    static List<String> keywordQueries(String message) {
        List<String> words = new ArrayList<String>();
        for (String word : message.split("[^\\p{L}\\p{N}_-]+")) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (word.length() >= 2 && !STOP_WORDS.contains(lower) && !words.contains(word)) {
                words.add(word);
            }
        }
        List<String> queries = new ArrayList<String>();
        for (String word : words) {
            if (queries.size() >= MAX_QUERIES - 1) {
                break;
            }
            queries.add(word);
        }
        if (words.size() > 1 && queries.size() < MAX_QUERIES) {
            queries.add(words.get(0) + " " + words.get(1));
        }
        if (queries.isEmpty() && !message.trim().isEmpty()) {
            queries.add(ToolNarration.shorten(message.trim()));
        }
        return queries;
    }

    /** Sucht, liest, notiert und beantwortet die Frage aus den gelesenen Quellen; die Recherche-Karte zeigt den Lauf. */
    String answer(String question, List<String> queries, ResearchAgentTurns.Output progress)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        progress.thought("Ich suche in den aktivierten Wissensquellen nach: " + join(queries));
        ResearchCard card = new ResearchCard(progress, queries, MAX_PAGES);
        card.searching();
        lastSucceeded = false;
        try {
            return research(question, queries, progress, card);
        } finally {
            card.finish(true, "");
        }
    }

    private String research(String question, List<String> queries, ResearchAgentTurns.Output progress,
                            ResearchCard card) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        List<KnowledgePages.Hit> hits = new ArrayList<KnowledgePages.Hit>();
        Set<String> seen = new HashSet<String>();
        List<List<KnowledgePages.Hit>> perQuery = new ArrayList<List<KnowledgePages.Hit>>();
        String failure = null;
        for (String query : queries) {
            if (progress.isCancelled()) {
                card.finish(false, "abgebrochen");
                return "Abgebrochen.";
            }
            try {
                perQuery.add(pages.search(query, HITS_PER_QUERY));
            } catch (ToolInvoker.ToolFailure failed) {
                LOG.info("[direct] Suche fehlgeschlagen: " + failed.getMessage());
                failure = failed.getMessage();
            }
        }
        if (perQuery.isEmpty() && failure != null) {
            card.finish(false, "Suche fehlgeschlagen: " + ToolNarration.shorten(failure));
            return "Die Suche in den Wissensquellen ist fehlgeschlagen: " + failure;
        }
        // abwechselnd je Anfrage, damit jede Anfrage ihren besten Treffer einbringt
        for (int rank = 0; rank < HITS_PER_QUERY; rank++) {
            for (List<KnowledgePages.Hit> list : perQuery) {
                if (rank < list.size() && seen.add(list.get(rank).id)) {
                    hits.add(list.get(rank));
                }
            }
        }
        hits = withinFence(hits);
        card.found(hits);
        if (hits.isEmpty()) {
            lastSucceeded = true; // gesucht ist gesucht, auch ohne Treffer
            card.finish(true, "keine Treffer");
            return "In den aktivierten Wissensquellen habe ich zu " + join(queries) + " nichts gefunden. Prüfe in "
                    + "den Einstellungen, ob die Quelle angehakt ist, oder nenne mir einen anderen Begriff.";
        }
        List<ResearchWriting.Passage> excerpts = new ArrayList<ResearchWriting.Passage>();
        Map<String, KnowledgePages.Hit> read = new LinkedHashMap<String, KnowledgePages.Hit>();
        for (KnowledgePages.Hit hit : hits) {
            if (read.size() >= MAX_PAGES || progress.isCancelled()) {
                break;
            }
            String title = hit.title.isEmpty() ? hit.id : hit.title;
            card.reading(title);
            // Das Budget zählt erfolgreich gelesene Seiten: ein unlesbarer Treffer macht Platz für den nächsten.
            String page = readPage(hit.id);
            if (page == null || page.trim().isEmpty()) {
                card.skipped(title);
                continue;
            }
            read.put(hit.id, hit);
            card.read(title, note(hit));
            excerpts.add(new ResearchWriting.Passage("page:" + hit.id, hit.id, hit.title,
                    excerpt(page, question + " " + join(queries))));
        }
        if (read.isEmpty()) {
            card.finish(false, "keine Seite lesbar");
            return "Ich habe " + hits.size() + " Treffer gefunden, konnte aber keine der Seiten lesen.";
        }
        card.answering();
        lastSucceeded = true;
        // Die Auszüge der eben gelesenen Seiten gehen immer mit: eine Projektpassage derselben Seite kann nur die
        // Notiz (Titel, Snippet) sein und darf den gelesenen Seitentext nie verdrängen.
        List<ResearchWriting.Passage> passages = new ArrayList<ResearchWriting.Passage>(excerpts);
        Set<String> texts = new HashSet<String>();
        for (ResearchWriting.Passage excerpt : excerpts) {
            texts.add(excerpt.text.trim());
        }
        Set<String> others = new HashSet<String>();
        for (ResearchWriting.Passage passage : projectPassages(question)) {
            String text = passage.text == null ? "" : passage.text.trim();
            if (text.isEmpty() || text.equals(passage.title.trim()) || !texts.add(text)) {
                continue;
            }
            if (read.containsKey(passage.resource)) {
                passages.add(passage);
            } else if (others.size() < PROJECT_PASSAGES || others.contains(passage.resource)) {
                others.add(passage.resource);
                passages.add(passage);
            }
        }
        String system = "Du bist der Recherche-Agent. Beantworte die Frage des Nutzers ausführlich und inhaltlich auf "
                + "Deutsch, ausschließlich aus den nummerierten Auszügen der Wissensquellen. Belege jede Aussage am "
                + "Satzende mit [P<n>]. Nenne Namen, Organisationseinheiten, Termine und Meilensteine genau so, wie sie "
                + "in den Auszügen stehen. Erfinde nichts. Fehlt etwas, sag am Ende knapp, was die Quellen nicht "
                + "beantworten. Behaupte nie, du hättest keinen Zugriff auf die Quellen: die Auszüge SIND die Quellen.";
        String text = ResearchWriting.clean(complete(system, "FRAGE: " + question.trim() + "\n\nAUSZÜGE:\n"
                + render(passages), 0.2d, OUTPUT_TOKENS));
        card.finish(true, card.summary());
        if (text.isEmpty()) {
            StringBuilder fallback = new StringBuilder("Das Modell hat keine Antwort geliefert. Gelesen und im "
                    + "Projekt notiert habe ich:");
            for (KnowledgePages.Hit hit : read.values()) {
                fallback.append("\n- ").append(hit.title.isEmpty() ? hit.id : hit.title);
            }
            return fallback.toString();
        }
        return ResearchWriting.withSourceList(text, passages);
    }

    /** Ob die letzte {@link #answer} gesucht hat (ohne Fehler, Abbruch oder unlesbare Seiten). */
    boolean lastSucceeded() {
        return lastSucceeded;
    }

    /** Treffer, die der Weidezaun ausschließt, fallen weg; ohne aktiven Zaun bleiben alle. */
    private List<KnowledgePages.Hit> withinFence(List<KnowledgePages.Hit> hits) {
        SearchScopeControlPort scope = new ServiceSearchScopeControlPort(host);
        SearchScopeControlPort.Session session;
        try {
            session = scope.begin();
        } catch (Exception unavailable) {
            LOG.info("[direct] Weidezaun nicht prüfbar, Treffer ungefiltert: " + unavailable.getMessage());
            return hits;
        }
        if (!session.active || !session.outFilter) {
            if (session.active) {
                scope.end(session.handle);
            }
            return hits;
        }
        try {
            List<SearchScopeControlPort.Item> items = new ArrayList<SearchScopeControlPort.Item>();
            for (KnowledgePages.Hit hit : hits) {
                items.add(new SearchScopeControlPort.Item(hit.id, hit.title + " " + hit.snippet));
            }
            Set<String> out = new HashSet<String>();
            for (SearchScopeControlPort.Decision decision : scope.evaluate(session.handle, "serp", items)) {
                if (decision.out) {
                    out.add(decision.id);
                }
            }
            List<KnowledgePages.Hit> kept = new ArrayList<KnowledgePages.Hit>();
            for (KnowledgePages.Hit hit : hits) {
                if (!out.contains(hit.id)) {
                    kept.add(hit);
                }
            }
            if (!out.isEmpty()) {
                LOG.info("[direct] Weidezaun: " + out.size() + " Treffer ausgeschlossen");
            }
            return kept;
        } catch (Exception unavailable) {
            LOG.info("[direct] Weidezaun nicht prüfbar, Treffer ungefiltert: " + unavailable.getMessage());
            return hits;
        } finally {
            scope.end(session.handle);
        }
    }

    /** Die gelesene Seite als Quelle des Projekts (der Host indexiert sie über seinen Worker-Pool). */
    private boolean note(KnowledgePages.Hit hit) throws ToolInvoker.EndpointUnavailable {
        Map<String, Object> add = new LinkedHashMap<String, Object>();
        add.put("capture_id", hit.id);
        add.put("title", hit.title);
        add.put("url", hit.url);
        try {
            String reply = host.call("project_source_add", add);
            return !ToolNarration.isFailureText(reply);
        } catch (ToolInvoker.ToolFailure refused) {
            LOG.info("[direct] Quelle nicht notiert: " + refused.getMessage());
            return false;
        }
    }

    private List<ResearchWriting.Passage> projectPassages(String question) throws ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", question);
        args.put("limit", Integer.valueOf(PROJECT_PASSAGES));
        try {
            return ResearchWriting.parsePassages(host.call("project_search", args));
        } catch (ToolInvoker.ToolFailure unavailable) {
            return new ArrayList<ResearchWriting.Passage>();
        }
    }

    /**
     * Der für die Frage beste Teil einer Seite: Absätze nach Anzahl der Suchbegriffe geordnet, in Seitenreihenfolge
     * bis {@value #PAGE_EXCERPT_CHARS} Zeichen; ohne Treffer der Anfang der Seite.
     */
    static String excerpt(String page, String terms) {
        String[] paragraphs = page.split("\\r?\\n\\s*\\r?\\n|\\r?\\n");
        Set<String> words = new HashSet<String>();
        for (String word : terms.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        int[] scores = new int[paragraphs.length];
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < paragraphs.length; i++) {
            String lower = paragraphs[i].toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (lower.contains(word)) {
                    scores[i]++;
                }
            }
            order.add(Integer.valueOf(i));
        }
        final int[] ranking = scores;
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return ranking[b.intValue()] != ranking[a.intValue()] ? ranking[b.intValue()] - ranking[a.intValue()]
                        : a.intValue() - b.intValue();
            }
        });
        boolean[] chosen = new boolean[paragraphs.length];
        int length = 0;
        for (Integer index : order) {
            String paragraph = paragraphs[index.intValue()].trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (length > 0 && length + paragraph.length() > PAGE_EXCERPT_CHARS) {
                continue;
            }
            chosen[index.intValue()] = true;
            length += paragraph.length() + 1;
            if (length >= PAGE_EXCERPT_CHARS) {
                break;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            if (chosen[i]) {
                text.append(text.length() == 0 ? "" : "\n").append(paragraphs[i].trim());
            }
        }
        return text.length() > PAGE_EXCERPT_CHARS ? text.substring(0, PAGE_EXCERPT_CHARS) + "…" : text.toString();
    }

    private static String render(List<ResearchWriting.Passage> passages) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            ResearchWriting.Passage passage = passages.get(i);
            text.append("[P").append(i + 1).append("] ")
                    .append(passage.title.isEmpty() ? passage.resource : passage.title).append(": ")
                    .append(passage.text.replaceAll("\\s*\\n\\s*", " ").trim()).append("\n\n");
        }
        return text.toString();
    }

    private static String join(List<String> queries) {
        StringBuilder text = new StringBuilder();
        for (String query : queries) {
            text.append(text.length() == 0 ? "" : ", ").append('„').append(query).append('“');
        }
        return text.toString();
    }

    private String complete(String system, String user, double temperature, int maxTokens) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        MainModelChatResult result = model.complete(messages, temperature, maxTokens);
        if (!result.isOk()) {
            LOG.warning("[direct] model failed: " + result.getStatus() + " " + result.getDetail());
            return "";
        }
        return result.getText() == null ? "" : result.getText();
    }
}
