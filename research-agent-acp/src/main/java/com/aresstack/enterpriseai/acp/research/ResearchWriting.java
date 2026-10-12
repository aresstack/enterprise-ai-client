package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Phasen nach der Quellensuche: Gliederung, Dokument und Nachfragen. Grundlage sind immer die übernommenen
 * Quellen des Projekts, gelesen als Passagen aus dem Wissensindex ({@code project_search}); das Projekt hält keine
 * Textkopien. Jede Aussage des Dokuments trägt ihre Passage ({@code [[passage-id]]}), der Host nummeriert daraus
 * die Quellen. Reicht der Projektkorpus für eine Nachfrage nicht, sucht der Agent in den aktivierten Wissensquellen
 * nach (dieselbe Recherche wie in der Phase Konzept: {@link DirectAnswers} mit Recherche-Karte und Weidezaun) und
 * übernimmt die gelesenen Seiten ins Projekt, in jeder Phase. Das Dokument entsteht Kapitel für Kapitel; jedes fertige
 * Kapitel speichert der Host sofort ({@code document_section_save}), ein Fehler bei Kapitel n lässt 1..n-1 bestehen,
 * und der nächste Schreibauftrag setzt bei Kapitel n fort ({@code document_draft}).
 */
final class ResearchWriting {

    private static final Logger LOG = Logger.getLogger(ResearchWriting.class.getName());
    private static final int OUTLINE_PASSAGES_PER_QUERY = 3;
    private static final int OUTLINE_QUERIES = 6;
    private static final int SECTION_PASSAGES = 8;
    private static final int ANSWER_PASSAGES = 8;
    /** Höchstens so viele Seiten übernimmt die Nachsuche eines Kapitels ohne Passagen (kippbar). */
    private static final int FOLLOW_UP_PAGES = 3;
    private static final int PASSAGE_CHARS = 1200;
    private static final int OUTPUT_TOKENS = 3000;
    static final String INSUFFICIENT = "UNZUREICHEND";
    private static final Pattern P_MARK = Pattern.compile("\\[P(\\d+)\\]");

    /** Eine Passage aus {@code project_search}. */
    static final class Passage {
        final String id;
        final String resource;
        final String title;
        final String text;

        Passage(String id, String resource, String title, String text) {
            this.id = id;
            this.resource = resource;
            this.title = title;
            this.text = text;
        }
    }

    /** Ein Kapitel der Gliederung mit seinen Leitfragen. */
    static final class Chapter {
        final String heading;
        final List<String> points = new ArrayList<String>();

        Chapter(String heading) {
            this.heading = heading;
        }
    }

    /** Ein Kapitel konnte nicht geschrieben werden (Modell); die früheren bleiben gespeichert. */
    static final class ChapterFailure extends Exception {
        ChapterFailure(String message) {
            super(message);
        }
    }

    private final ToolInvoker host;
    private final KnowledgePages pages;
    private final MainModelChat model;
    private final DirectAnswers direct;

    ResearchWriting(ToolInvoker host, MainModelChat model) {
        this(host, model, new DirectAnswers(host, model,
                () -> EnvironmentRunBudget.from(System.getenv()).getMaxPagesVisited()));
    }

    ResearchWriting(ToolInvoker host, MainModelChat model, DirectAnswers direct) {
        this.host = host;
        this.pages = new KnowledgePages(host);
        this.model = model;
        this.direct = direct;
    }

    // ------------------------------------------------------------------ Gliederung

    /**
     * Erstellt (oder überarbeitet mit {@code feedback}) die Gliederung aus Konzept, Weidezaun, Quellen und deren
     * Passagen und legt sie zur Freigabe vor.
     */
    String outline(String concept, String fence, String feedback)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String sources = host.call("research_sources", null);
        String previous = host.call("research_outline", null);
        Map<String, Passage> passages = new LinkedHashMap<String, Passage>();
        List<String> queries = lines(host.call("research_search_plan", null));
        for (String query : queries.subList(0, Math.min(OUTLINE_QUERIES, queries.size()))) {
            for (Passage passage : search(query, OUTLINE_PASSAGES_PER_QUERY)) {
                passages.put(passage.id, passage);
            }
        }
        StringBuilder user = new StringBuilder();
        user.append("KONZEPT:\n").append(concept.trim()).append("\n\n");
        if (!fence.trim().isEmpty()) {
            user.append("WEIDEZAUN (verbindlicher Umfang, Ausgeschlossenes nie behandeln):\n").append(fence.trim())
                    .append("\n\n");
        }
        user.append("ÜBERNOMMENE QUELLEN (resource-ID, Titel, URL):\n").append(sources.trim()).append("\n\n");
        user.append("AUSZÜGE AUS DEN QUELLEN:\n").append(render(new ArrayList<Passage>(passages.values()), false));
        if (!previous.trim().isEmpty()) {
            user.append("\nBISHERIGE GLIEDERUNG:\n").append(previous.trim()).append("\n");
        }
        if (feedback != null && !feedback.trim().isEmpty()) {
            user.append("\nÄNDERUNGSWUNSCH DES NUTZERS:\n").append(feedback.trim()).append("\n");
        }
        String system = "Du erstellst die Gliederung eines ausführlichen Rechercheberichts auf Deutsch. Grundlage sind "
                + "ausschließlich Konzept, Weidezaun und die übernommenen Quellen. Antworte NUR mit Markdown in "
                + "diesem Format: eine Zeile '# Titel', dann je Kapitel eine Zeile '## <Nummer>. <Kapitel>' und "
                + "darunter Zeilen '- <Leitfrage, die das Kapitel beantwortet>'. Decke alle Fragen des Konzepts ab "
                + "(z. B. Zweck, Ziele, Architektur/Funktionen, Planung, Meilensteine, beteiligte Personen oder "
                + "Organisationseinheiten, soweit sie zum Auftrag gehören). Das letzte Kapitel heißt "
                + "'Offene Punkte und fehlende Informationen'. Keine Einleitung, kein Schlusssatz.";
        String outline = clean(complete(system, user.toString(), 0.2d));
        if (outline.isEmpty()) {
            return "Die Gliederung konnte nicht erstellt werden: das Modell lieferte keine Antwort.";
        }
        if (!outline.startsWith("# ")) {
            outline = "# Recherchebericht\n\n" + outline;
        }
        host.call("outline_propose", single("outline", outline));
        return "Hier ist mein Vorschlag für die Gliederung:\n\n" + outline + "\n\nDu kannst sie im Reiter "
                + "„Gliederung“ direkt korrigieren oder Änderungen hier im Chat beschreiben. Wähle danach die Phase "
                + "„Dokument“ und starte dort „Dokument schreiben“.";
    }

    // ------------------------------------------------------------------ Dokument

    /**
     * Schreibt das Dokument Kapitel für Kapitel mit Passagen-Belegen. Jedes fertige Kapitel geht sofort an den Host
     * ({@code document_section_save}); scheitert ein Kapitel, bleiben die früheren gespeichert und der nächste Aufruf
     * setzt beim gescheiterten fort. Erst das vollständige Dokument wird gespeichert ({@code document_save}).
     */
    String document(ResearchAgentTurns.Output progress) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String outline = host.call("research_outline", null);
        String title = titleOf(outline);
        List<Chapter> chapters = chaptersOf(outline);
        if (chapters.isEmpty()) {
            return "Die Gliederung hat keine Kapitel; bitte ergänze sie im Reiter „Gliederung“.";
        }
        List<String[]> done = savedChapters(chapters);
        Map<String, String> passageResources = new LinkedHashMap<String, String>();
        List<String> texts = new ArrayList<String>();
        StringBuilder written = new StringBuilder();
        for (String[] saved : done) {
            texts.add(saved[1]);
            passageResources.putAll(parsePassageMap(saved[2]));
            written.append("## ").append(saved[0]).append("\n").append(saved[1]).append("\n\n");
        }
        if (!done.isEmpty() && done.size() < chapters.size()) {
            progress.thought(chaptersKept(done.size()) + " Ich setze bei Kapitel " + (done.size() + 1) + " fort.");
        }
        for (int index = done.size(); index < chapters.size(); index++) {
            Chapter chapter = chapters.get(index);
            if (progress.isCancelled()) {
                return "Abgebrochen. " + chaptersKept(index)
                        + " Mit „Dokument schreiben“ im Reiter „Dokument“ geht es bei Kapitel "
                        + (index + 1) + " weiter.";
            }
            progress.thought("Schreibe Abschnitt " + (index + 1) + " von " + chapters.size() + ": "
                    + chapter.heading);
            Map<String, String> chapterPassages = new LinkedHashMap<String, String>();
            String text;
            try {
                if (isOpenPointsChapter(chapter.heading)) {
                    text = openPoints(chapter, outline, written.toString());
                } else {
                    String query = chapter.heading + " " + join(chapter.points);
                    List<Passage> passages = search(query, SECTION_PASSAGES);
                    if (passages.isEmpty()) {
                        passages = followUpPassages(chapterTopic(chapter), query, progress);
                    }
                    text = section(title, chapter, passages);
                    text = toPassageMarkers(text, passages, chapterPassages);
                }
                Map<String, Object> args = new LinkedHashMap<String, Object>();
                args.put("index", Integer.valueOf(index));
                args.put("heading", chapter.heading);
                args.put("text", text);
                args.put("passages_json", passageMapJson(chapterPassages));
                host.call("document_section_save", args);
            } catch (ChapterFailure failed) {
                return chapterFailed(index, chapter, failed.getMessage());
            } catch (ToolInvoker.ToolFailure failed) {
                return chapterFailed(index, chapter, failed.getMessage());
            }
            passageResources.putAll(chapterPassages);
            texts.add(text);
            written.append("## ").append(chapter.heading).append("\n").append(text).append("\n\n");
        }
        StringBuilder json = new StringBuilder("{\"title\":").append(ResearchHostControl.quote(title))
                .append(",\"sections\":[");
        for (int index = 0; index < chapters.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"heading\":").append(ResearchHostControl.quote(chapters.get(index).heading))
                    .append(",\"text\":").append(ResearchHostControl.quote(texts.get(index))).append('}');
        }
        json.append("],\"passages\":").append(passageMapJson(passageResources)).append('}');
        host.call("document_save", single("document_json", json.toString()));
        return "Das Dokument „" + title + "“ ist fertig (" + chapters.size() + " Abschnitte) und steht im Reiter "
                + "„Dokument“; jede belegte Aussage verweist auf ihre Quelle. Du kannst jetzt Detailfragen stellen; "
                + "ich suche zuerst in den Quellen dieses Projekts und bei Bedarf in den Wissensquellen nach.";
    }

    /**
     * Ein Kapitel ohne Passagen im Projektkorpus: der Agent sucht wie bei einer Nachfrage in den Wissensquellen nach,
     * übernimmt die gelesenen Seiten ins Projekt und sucht dann erneut im Projektkorpus. Ohne Wissenssuche (oder ohne
     * Treffer) bleibt es leer; das Kapitel sagt dann, dass die Quellen nichts enthalten.
     */
    private List<Passage> followUpPassages(String topic, String query, ResearchAgentTurns.Output progress)
            throws ToolInvoker.EndpointUnavailable {
        int added = 0;
        try {
            for (KnowledgePages.Hit hit : pages.search(topic, FOLLOW_UP_PAGES * 2)) {
                if (added >= FOLLOW_UP_PAGES || progress.isCancelled()) {
                    break;
                }
                String page = direct.readPage(hit.id); // ein unlesbarer Treffer wird übersprungen
                if (page == null || page.trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> add = new LinkedHashMap<String, Object>();
                add.put("capture_id", hit.id);
                add.put("title", hit.title);
                add.put("url", hit.url);
                String result = host.call("project_source_add", add);
                if (!result.startsWith("ALREADY_ACCEPTED")) {
                    progress.thought("Neue Quelle im Projekt: " + (hit.title.isEmpty() ? hit.id : hit.title));
                }
                added++;
            }
        } catch (ToolInvoker.ToolFailure unavailable) {
            LOG.info("[research-writing] Nachsuche nicht möglich: " + unavailable.getMessage());
        }
        if (added == 0) {
            return new ArrayList<Passage>();
        }
        try {
            return search(query, SECTION_PASSAGES);
        } catch (ToolInvoker.ToolFailure unavailable) {
            return new ArrayList<Passage>();
        }
    }

    /** Die Suchanfrage einer Nachsuche: die Kapitelüberschrift ohne Nummer. */
    static String chapterTopic(Chapter chapter) {
        return chapter.heading.replaceFirst("^\\d+(\\.\\d+)*\\.?\\s*", "").trim();
    }

    /** „Kapitel 1–3 sind gespeichert.“ */
    static String chaptersKept(int count) {
        if (count <= 0) {
            return "Es ist noch kein Kapitel gespeichert.";
        }
        return count == 1 ? "Kapitel 1 ist gespeichert." : "Kapitel 1–" + count + " sind gespeichert.";
    }

    private static String chapterFailed(int index, Chapter chapter, String reason) {
        LOG.warning("[research-writing] Kapitel " + (index + 1) + " gescheitert: " + reason);
        return "Kapitel " + (index + 1) + " („" + chapter.heading + "“) konnte nicht geschrieben werden: " + reason
                + "\n\n" + chaptersKept(index) + " Mit „Dokument schreiben“ im Reiter „Dokument“ schreibe ich ab "
                + "Kapitel " + (index + 1) + " weiter.";
    }

    /**
     * Die schon gespeicherten Kapitel ({@code heading, text, passages-JSON}), soweit sie der Reihe nach zur
     * aktuellen Gliederung passen; ohne Zwischenstand leer.
     */
    private List<String[]> savedChapters(List<Chapter> chapters) throws ToolInvoker.EndpointUnavailable {
        List<String[]> saved = new ArrayList<String[]>();
        Object parsed;
        try {
            String json = host.call("document_draft", null);
            parsed = MiniJson.parse(json == null || json.trim().isEmpty() ? "{}" : json);
        } catch (ToolInvoker.ToolFailure unavailable) {
            return saved;
        } catch (RuntimeException unreadable) {
            return saved;
        }
        if (!(parsed instanceof Map) || !(((Map<?, ?>) parsed).get("sections") instanceof List)) {
            return saved;
        }
        List<?> sections = (List<?>) ((Map<?, ?>) parsed).get("sections");
        for (int index = 0; index < sections.size() && index < chapters.size(); index++) {
            if (!(sections.get(index) instanceof Map)) {
                break;
            }
            Map<?, ?> section = (Map<?, ?>) sections.get(index);
            Object text = section.get("text");
            if (!chapters.get(index).heading.equals(String.valueOf(section.get("heading")))
                    || !(text instanceof String)) {
                break;
            }
            Map<String, String> passages = new LinkedHashMap<String, String>();
            if (section.get("passages") instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) section.get("passages")).entrySet()) {
                    passages.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            saved.add(new String[]{chapters.get(index).heading, (String) text, passageMapJson(passages)});
        }
        return saved;
    }

    private static Map<String, String> parsePassageMap(String json) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        Object parsed;
        try {
            parsed = MiniJson.parse(json);
        } catch (RuntimeException unreadable) {
            return map;
        }
        if (parsed instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
                map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return map;
    }

    private static String passageMapJson(Map<String, String> passages) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, String> entry : passages.entrySet()) {
            json.append(json.length() > 1 ? "," : "").append(ResearchHostControl.quote(entry.getKey())).append(':')
                    .append(ResearchHostControl.quote(entry.getValue()));
        }
        return json.append('}').toString();
    }

    private String section(String title, Chapter chapter, List<Passage> passages) throws ChapterFailure {
        String system = "Du schreibst einen Abschnitt eines ausführlichen Rechercheberichts auf Deutsch. Nutze "
                + "AUSSCHLIESSLICH die nummerierten Passagen. Belege jede Aussage direkt am Satzende mit der Passage, "
                + "z. B. [P2] oder [P1][P3]. Erfinde nichts. Nenne Namen, Organisationseinheiten, Termine und "
                + "Meilensteine genau so, wie sie in den Passagen stehen. Beantwortet keine Passage eine Leitfrage, "
                + "schreibe ausdrücklich, dass die Quellen dazu keine Angaben enthalten. Antworte nur mit dem "
                + "Fließtext des Abschnitts (mehrere Absätze), ohne Überschrift.";
        StringBuilder user = new StringBuilder();
        user.append("BERICHT: ").append(title).append("\nABSCHNITT: ").append(chapter.heading).append('\n');
        if (!chapter.points.isEmpty()) {
            user.append("LEITFRAGEN:\n");
            for (String point : chapter.points) {
                user.append("- ").append(point).append('\n');
            }
        }
        user.append("\nPASSAGEN:\n").append(passages.isEmpty() ? "(keine passenden Passagen in den Quellen)\n"
                : render(passages, true));
        String text = clean(completeOrFail(system, user.toString(), 0.2d));
        return text.isEmpty() ? "Die Quellen des Projekts enthalten zu diesem Abschnitt keine Angaben." : text;
    }

    private String openPoints(Chapter chapter, String outline, String written) throws ChapterFailure {
        String system = "Du prüfst einen Rechercheberichts-Entwurf auf Lücken. Liste auf Deutsch knapp als "
                + "Aufzählung, welche Leitfragen der Gliederung die Quellen nicht oder nur teilweise beantworten und "
                + "welche Informationen fehlen. Erfinde keine Antworten.";
        String text = clean(completeOrFail(system, "GLIEDERUNG:\n" + outline + "\n\nENTWURF:\n" + written, 0.1d));
        return text.isEmpty() ? "Keine offenen Punkte festgestellt." : text;
    }

    // ------------------------------------------------------------------ Nachfragen

    /**
     * Beantwortet eine Frage zuerst aus dem Quellenkorpus des Projekts. Reicht er nicht, recherchiert der Agent in
     * den aktivierten Wissensquellen nach (dieselbe Recherche wie in der Phase Konzept, mit Karte, Weidezaun und
     * Übernahme der gelesenen Seiten ins Projekt), in jeder Phase.
     *
     * @param searchAvailable ob die Wissenssuche angeboten wird; ohne sie bleibt es beim Projektkorpus
     */
    String answer(String question, boolean searchAvailable, ResearchAgentTurns.Output progress)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String fromCorpus = corpusAnswer(question);
        if (fromCorpus != null) {
            return fromCorpus;
        }
        if (progress.isCancelled()) {
            return "Abgebrochen.";
        }
        if (!searchAvailable) {
            return "Die Quellen dieses Projekts beantworten das nicht, und die Wissenssuche ist gerade nicht aktiv. "
                    + "Prüfe in den Einstellungen unter „Tools“, ob knowledge_search und knowledge_read aktiv sind.";
        }
        progress.thought("Die Quellen des Projekts reichen dafür nicht; ich suche in den Wissensquellen nach.");
        String fence = "";
        try {
            fence = host.call("research_scope_fence", null);
        } catch (ToolInvoker.ToolFailure unavailable) {
            LOG.fine("[research-writing] kein Weidezaun: " + unavailable.getMessage());
        }
        List<String> queries = direct.plan(question, "", fence);
        if (queries.isEmpty()) {
            queries = DirectAnswers.keywordQueries(question);
        }
        return direct.answer(question, queries, progress);
    }

    /**
     * Die Antwort allein aus dem Projektkorpus, oder {@code null}, wenn er nichts Passendes enthält (ohne
     * Modellaufruf, wenn keine Passage gefunden wird).
     */
    String corpusAnswer(String question) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        List<Passage> passages = search(question, ANSWER_PASSAGES);
        if (passages.isEmpty()) {
            return null;
        }
        String answer = answerFrom(question, passages);
        return INSUFFICIENT.equals(answer) ? null : withSourceList(answer, passages);
    }

    private String answerFrom(String question, List<Passage> passages) {
        String system = "Beantworte die Frage auf Deutsch ausschließlich aus den nummerierten Passagen und belege jede "
                + "Aussage mit [P<n>]. Erfinde nichts. Reichen die Passagen nicht aus, antworte nur mit dem Wort "
                + INSUFFICIENT + ".";
        String text = clean(complete(system, "FRAGE: " + question + "\n\nPASSAGEN:\n" + render(passages, true), 0.1d));
        if (text.isEmpty() || text.toUpperCase(Locale.ROOT).startsWith(INSUFFICIENT)) {
            return INSUFFICIENT;
        }
        return text;
    }

    /** [P2] → [1] (Nummer der Quelle in dieser Antwort) samt Quellenliste darunter. */
    static String withSourceList(String answer, List<Passage> passages) {
        Map<String, Integer> numbers = new LinkedHashMap<String, Integer>();
        Map<String, Passage> byResource = new LinkedHashMap<String, Passage>();
        Matcher marks = P_MARK.matcher(answer);
        StringBuffer text = new StringBuffer();
        while (marks.find()) {
            int index = Integer.parseInt(marks.group(1)) - 1;
            String replacement = "";
            if (index >= 0 && index < passages.size()) {
                Passage passage = passages.get(index);
                Integer number = numbers.get(passage.resource);
                if (number == null) {
                    number = numbers.size() + 1;
                    numbers.put(passage.resource, number);
                    byResource.put(passage.resource, passage);
                }
                replacement = "[" + number + "]";
            }
            marks.appendReplacement(text, Matcher.quoteReplacement(replacement));
        }
        marks.appendTail(text);
        String result = text.toString().replace("[1][1]", "[1]").trim();
        if (numbers.isEmpty()) {
            return result;
        }
        StringBuilder list = new StringBuilder(result).append("\n\nQuellen:");
        for (Map.Entry<String, Integer> number : numbers.entrySet()) {
            Passage passage = byResource.get(number.getKey());
            list.append("\n[").append(number.getValue()).append("] ")
                    .append(passage.title.isEmpty() ? passage.resource : passage.title);
        }
        return list.toString();
    }

    // ------------------------------------------------------------------ helpers

    List<Passage> search(String query, int limit) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", query);
        args.put("limit", Integer.valueOf(limit));
        return parsePassages(host.call("project_search", args));
    }

    static List<Passage> parsePassages(String text) {
        List<Passage> passages = new ArrayList<Passage>();
        if (text == null) {
            return passages;
        }
        for (String block : text.split("\\r?\\n\\s*\\r?\\n")) {
            String id = "";
            String resource = "";
            String title = "";
            StringBuilder body = new StringBuilder();
            for (String line : block.split("\\r?\\n")) {
                if (line.startsWith("passage: ") && body.length() == 0) {
                    id = line.substring("passage: ".length()).trim();
                } else if (line.startsWith("resource: ") && body.length() == 0) {
                    resource = line.substring("resource: ".length()).trim();
                } else if (line.startsWith("title: ") && body.length() == 0) {
                    title = line.substring("title: ".length()).trim();
                } else if (!line.trim().isEmpty()) {
                    body.append(body.length() == 0 ? "" : " ").append(line.trim());
                }
            }
            if (!id.isEmpty() && !resource.isEmpty()) {
                passages.add(new Passage(id, resource, title, body.toString()));
            }
        }
        return passages;
    }

    private static String render(List<Passage> passages, boolean numbered) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            Passage passage = passages.get(i);
            String body = passage.text.length() > PASSAGE_CHARS ? passage.text.substring(0, PASSAGE_CHARS) + "…"
                    : passage.text;
            text.append(numbered ? "[P" + (i + 1) + "] " : "- ").append(passage.title.isEmpty() ? passage.resource
                    : passage.title).append(": ").append(body).append("\n\n");
        }
        return text.toString();
    }

    /** [P2] → [[passage-id]], die Passage merkt sich ihre Ressource. */
    static String toPassageMarkers(String text, List<Passage> passages, Map<String, String> passageResources) {
        Matcher marks = P_MARK.matcher(text);
        StringBuffer result = new StringBuffer();
        while (marks.find()) {
            int index = Integer.parseInt(marks.group(1)) - 1;
            String replacement = "";
            if (index >= 0 && index < passages.size()) {
                Passage passage = passages.get(index);
                passageResources.put(passage.id, passage.resource);
                replacement = "[[" + passage.id + "]]";
            }
            marks.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        marks.appendTail(result);
        return result.toString();
    }

    static String titleOf(String outline) {
        for (String line : lines(outline)) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return "Recherchebericht";
    }

    /** Kapitel = {@code ##}-Zeilen, Leitfragen = Aufzählungszeilen darunter. */
    static List<Chapter> chaptersOf(String outline) {
        List<Chapter> chapters = new ArrayList<Chapter>();
        Chapter current = null;
        for (String line : lines(outline)) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                current = new Chapter(line.replaceFirst("^#+\\s*", "").trim());
                chapters.add(current);
            } else if (current != null && (line.startsWith("- ") || line.startsWith("* "))) {
                current.points.add(line.substring(2).trim());
            }
        }
        return chapters;
    }

    private static boolean isOpenPointsChapter(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT);
        return lower.contains("offene punkte") || lower.contains("fehlende informationen");
    }

    private String complete(String system, String user, double temperature) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        MainModelChatResult result = model.complete(messages, temperature, OUTPUT_TOKENS);
        if (!result.isOk()) {
            LOG.warning("[research-writing] model failed: " + result.getStatus() + " " + result.getDetail());
            return "";
        }
        return result.getText() == null ? "" : result.getText();
    }

    /** Wie {@link #complete}, aber ein Modellfehler bricht das Kapitel ab, statt es leer zu füllen. */
    private String completeOrFail(String system, String user, double temperature) throws ChapterFailure {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        MainModelChatResult result = model.complete(messages, temperature, OUTPUT_TOKENS);
        if (!result.isOk()) {
            String detail = result.getDetail() == null || result.getDetail().isEmpty() ? result.getStatus().name()
                    : result.getStatus() + " " + result.getDetail();
            throw new ChapterFailure("das Modell hat nicht geantwortet (" + detail + ")");
        }
        return result.getText() == null ? "" : result.getText();
    }

    /** Ohne Codezaun und Rahmenleerzeilen. */
    static String clean(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            trimmed = firstBreak < 0 ? "" : trimmed.substring(firstBreak + 1);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static String join(List<String> points) {
        Set<String> unique = new LinkedHashSet<String>(points);
        StringBuilder text = new StringBuilder();
        for (String point : unique) {
            text.append(text.length() == 0 ? "" : " ").append(point);
        }
        return text.toString();
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put(key, value == null ? "" : value);
        return args;
    }
}
