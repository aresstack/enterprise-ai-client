package com.aresstack.enterpriseai.acp.research;

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
 * nach ({@code knowledge_search}/{@code knowledge_read}) und übernimmt passende Seiten ins Projekt.
 */
final class ResearchWriting {

    private static final Logger LOG = Logger.getLogger(ResearchWriting.class.getName());
    private static final int OUTLINE_PASSAGES_PER_QUERY = 3;
    private static final int OUTLINE_QUERIES = 6;
    private static final int SECTION_PASSAGES = 8;
    private static final int ANSWER_PASSAGES = 8;
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

    private final ToolInvoker host;
    private final KnowledgePages pages;
    private final MainModelChat model;

    ResearchWriting(ToolInvoker host, MainModelChat model) {
        this.host = host;
        this.pages = new KnowledgePages(host);
        this.model = model;
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
                + "„Gliederung“ direkt korrigieren oder Änderungen hier im Chat beschreiben. Mit „Gliederung "
                + "freigeben“ schreibe ich das Dokument.";
    }

    // ------------------------------------------------------------------ Dokument

    /** Schreibt das Dokument Kapitel für Kapitel mit Passagen-Belegen und speichert es im Projekt. */
    String document(ResearchAgentTurns.Output progress) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String outline = host.call("research_outline", null);
        String title = titleOf(outline);
        List<Chapter> chapters = chaptersOf(outline);
        if (chapters.isEmpty()) {
            return "Die Gliederung hat keine Kapitel; bitte ergänze sie im Reiter „Gliederung“.";
        }
        StringBuilder json = new StringBuilder("{\"title\":").append(ResearchHostControl.quote(title))
                .append(",\"sections\":[");
        Map<String, String> passageResources = new LinkedHashMap<String, String>();
        StringBuilder written = new StringBuilder();
        for (int index = 0; index < chapters.size(); index++) {
            if (progress.isCancelled()) {
                return "Abgebrochen; das Dokument wurde nicht gespeichert.";
            }
            Chapter chapter = chapters.get(index);
            progress.message("Schreibe Abschnitt " + (index + 1) + " von " + chapters.size() + ": "
                    + chapter.heading);
            String text;
            if (isOpenPointsChapter(chapter.heading)) {
                text = openPoints(chapter, outline, written.toString());
            } else {
                List<Passage> passages = search(chapter.heading + " " + join(chapter.points), SECTION_PASSAGES);
                text = section(title, chapter, passages);
                text = toPassageMarkers(text, passages, passageResources);
            }
            written.append("## ").append(chapter.heading).append("\n").append(text).append("\n\n");
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"heading\":").append(ResearchHostControl.quote(chapter.heading)).append(",\"text\":")
                    .append(ResearchHostControl.quote(text)).append('}');
        }
        json.append("],\"passages\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : passageResources.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(ResearchHostControl.quote(entry.getKey())).append(':')
                    .append(ResearchHostControl.quote(entry.getValue()));
        }
        json.append("}}");
        host.call("document_save", single("document_json", json.toString()));
        return "Das Dokument „" + title + "“ ist fertig (" + chapters.size() + " Abschnitte) und steht im Reiter "
                + "„Dokument“; jede belegte Aussage verweist auf ihre Quelle. Du kannst jetzt Detailfragen stellen; "
                + "ich suche zuerst in den Quellen dieses Projekts und bei Bedarf in den Wissensquellen nach.";
    }

    private String section(String title, Chapter chapter, List<Passage> passages) {
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
        String text = clean(complete(system, user.toString(), 0.2d));
        return text.isEmpty() ? "Die Quellen des Projekts enthalten zu diesem Abschnitt keine Angaben." : text;
    }

    private String openPoints(Chapter chapter, String outline, String written) {
        String system = "Du prüfst einen Rechercheberichts-Entwurf auf Lücken. Liste auf Deutsch knapp als "
                + "Aufzählung, welche Leitfragen der Gliederung die Quellen nicht oder nur teilweise beantworten und "
                + "welche Informationen fehlen. Erfinde keine Antworten.";
        String text = clean(complete(system, "GLIEDERUNG:\n" + outline + "\n\nENTWURF:\n" + written, 0.1d));
        return text.isEmpty() ? "Keine offenen Punkte festgestellt." : text;
    }

    // ------------------------------------------------------------------ Nachfragen

    /**
     * Beantwortet eine Frage aus dem Quellenkorpus des Projekts. Reicht er nicht, sucht der Agent in den aktivierten
     * Wissensquellen nach, übernimmt passende Seiten (nur in der Phase Dokument) und antwortet erneut.
     */
    String answer(String question, boolean mayAddSources, ResearchAgentTurns.Output progress)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        List<Passage> passages = search(question, ANSWER_PASSAGES);
        String answer = passages.isEmpty() ? INSUFFICIENT : answerFrom(question, passages);
        if (INSUFFICIENT.equals(answer) && !progress.isCancelled()) {
            progress.message("Die Quellen des Projekts reichen dafür nicht; ich suche in den Wissensquellen nach.");
            int added = 0;
            for (KnowledgePages.Hit hit : pages.search(question, FOLLOW_UP_PAGES * 2)) {
                if (added >= FOLLOW_UP_PAGES || progress.isCancelled()) {
                    break;
                }
                Map<String, Object> read = new LinkedHashMap<String, Object>();
                read.put("id", hit.id);
                String page = host.call(KnowledgePages.READ_TOOL, read);
                if (page == null || page.trim().isEmpty()) {
                    continue;
                }
                if (mayAddSources) {
                    Map<String, Object> add = new LinkedHashMap<String, Object>();
                    add.put("capture_id", hit.id);
                    add.put("title", hit.title);
                    add.put("url", hit.url);
                    String result = host.call("project_source_add", add);
                    if (!result.startsWith("ALREADY_ACCEPTED")) {
                        progress.message("Neue Quelle im Projekt: " + (hit.title.isEmpty() ? hit.id : hit.title));
                    }
                }
                added++;
            }
            passages = mayAddSources ? search(question, ANSWER_PASSAGES) : passages;
            answer = passages.isEmpty() ? INSUFFICIENT : answerFrom(question, passages);
        }
        if (INSUFFICIENT.equals(answer)) {
            return "Dazu finde ich weder in den Quellen dieses Projekts noch in den aktivierten Wissensquellen "
                    + "belastbare Angaben.";
        }
        return withSourceList(answer, passages);
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
