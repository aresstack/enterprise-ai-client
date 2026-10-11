package com.aresstack.enterpriseai.acp.research;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Was der Nutzer von einem Werkzeugaufruf sieht (askai arch: gelbe Tool-Blasen, grüne Denkblase): Titel, Art,
 * Eingabe und eine kurze Ergebniszeile je Host-Werkzeug. Interne Werkzeuge (Status, Turn-Beginn, Fortschritt,
 * Weidezaun-Prüfung, Lesen des Konzepts als Kontext) bleiben unsichtbar.
 */
final class ToolNarration {

    /** Eine sichtbare Werkzeugzeile; {@code kind} ist die ACP-{@code ToolKind} in Kleinbuchstaben. */
    static final class Visible {
        final String title;
        final String kind;
        final String input;

        Visible(String title, String kind, String input) {
            this.title = title;
            this.kind = kind;
            this.input = input;
        }
    }

    private static final int SHORT = 160;

    /** Dokument-ID → Titel aus den Suchtreffern, damit „Lese Seite“ den Titel statt der ID zeigt. */
    private final Map<String, String> titles = new ConcurrentHashMap<String, String>();

    /** Die Blase eines Aufrufs, oder {@code null}, wenn er unsichtbar bleibt. */
    Visible describe(String tool, Map<String, Object> args) {
        if (KnowledgePages.SEARCH_TOOL.equals(tool)) {
            return new Visible("Suche in den Wissensquellen", "search", arg(args, "query"));
        }
        if (KnowledgePages.READ_TOOL.equals(tool)) {
            String id = arg(args, "id");
            String title = titles.get(id);
            return new Visible("Lese Seite", "read", title == null || title.isEmpty() ? id : title);
        }
        if ("project_search".equals(tool)) {
            return new Visible("Suche in den Quellen des Projekts", "search", arg(args, "query"));
        }
        if ("project_source_add".equals(tool)) {
            return new Visible("Quelle notieren", "edit", firstNonEmpty(arg(args, "title"), arg(args, "capture_id")));
        }
        if ("source_accept".equals(tool)) {
            return new Visible("Quelle übernehmen", "edit", firstNonEmpty(arg(args, "title"), arg(args, "capture_id")));
        }
        if ("source_park".equals(tool)) {
            return new Visible("Treffer zurückstellen", "edit", firstNonEmpty(arg(args, "title"), arg(args, "url")));
        }
        if ("main_model_complete".equals(tool)) {
            return new Visible("Denke nach", "think", purpose(arg(args, "messages_json")));
        }
        if ("concept_add".equals(tool)) {
            return new Visible("Konzept: Karte anlegen", "edit", arg(args, "name"));
        }
        if ("concept_add_cards".equals(tool)) {
            return new Visible("Konzept: Karten anlegen", "edit", jsonList(arg(args, "names_json")));
        }
        if ("concept_rename".equals(tool)) {
            return new Visible("Konzept: Karte umbenennen", "edit", arg(args, "name"));
        }
        if ("concept_move_leaf".equals(tool)) {
            return new Visible("Konzept: Karte verschieben", "move", jsonList(arg(args, "source_json")));
        }
        if ("exclude_topic".equals(tool)) {
            return new Visible("Weidezaun: Thema ausschließen", "edit", arg(args, "topic"));
        }
        if ("resolve_concept_conflict".equals(tool)) {
            return new Visible("Konzept: Konflikt auflösen", "edit", arg(args, "decision"));
        }
        if ("offer_searches".equals(tool)) {
            return new Visible("Suchvorschläge notieren", "other", jsonList(arg(args, "suggestions_json")));
        }
        if ("outline_propose".equals(tool)) {
            return new Visible("Gliederung vorlegen", "edit", ResearchWriting.titleOf(arg(args, "outline")));
        }
        if ("document_save".equals(tool)) {
            return new Visible("Dokument speichern", "edit", "");
        }
        return null;
    }

    /** Die Ergebniszeile; merkt sich nebenbei die Titel der Suchtreffer. */
    String summarize(String tool, String result) {
        String text = result == null ? "" : result.trim();
        if (KnowledgePages.SEARCH_TOOL.equals(tool)) {
            List<KnowledgePages.Hit> hits = KnowledgePages.parseHits(text);
            for (KnowledgePages.Hit hit : hits) {
                titles.put(hit.id, hit.title);
            }
            if (hits.isEmpty()) {
                return "Keine Treffer";
            }
            StringBuilder line = new StringBuilder(hits.size() == 1 ? "1 Treffer: " : hits.size() + " Treffer: ");
            for (int i = 0; i < Math.min(3, hits.size()); i++) {
                line.append(i == 0 ? "" : ", ").append(hits.get(i).title.isEmpty() ? hits.get(i).id
                        : hits.get(i).title);
            }
            return shorten(line.append(hits.size() > 3 ? ", …" : "").toString());
        }
        if (KnowledgePages.READ_TOOL.equals(tool)) {
            return text.isEmpty() ? "Seite ist leer" : "Gelesen (" + text.length() + " Zeichen)";
        }
        if ("project_search".equals(tool)) {
            int passages = ResearchWriting.parsePassages(text).size();
            return passages == 0 ? "Keine Passagen" : passages == 1 ? "1 Passage" : passages + " Passagen";
        }
        if ("main_model_complete".equals(tool)) {
            return "fertig";
        }
        if (text.startsWith("ALREADY_ACCEPTED")) {
            return "schon im Projekt";
        }
        return shorten(firstLine(text));
    }

    /** Antworten, die der Host als Text statt als Fehler liefert. */
    static boolean isFailureText(String result) {
        String text = result == null ? "" : result.trim();
        return text.startsWith("Tool failed:") || text.startsWith("Not allowed");
    }

    /** Wofür das Modell gefragt wird: der erste Satz seiner Systemnachricht. */
    static String purpose(String messagesJson) {
        Object parsed;
        try {
            parsed = com.aresstack.enterpriseai.research.runtime.json.MiniJson.parse(messagesJson);
        } catch (RuntimeException invalid) {
            return "";
        }
        if (!(parsed instanceof List)) {
            return "";
        }
        for (Object element : (List<?>) parsed) {
            if (element instanceof Map && "system".equals(((Map<?, ?>) element).get("role"))) {
                Object content = ((Map<?, ?>) element).get("content");
                String text = content == null ? "" : firstLine(content.toString());
                int end = text.indexOf(". ");
                return shorten(end > 0 ? text.substring(0, end + 1) : text);
            }
        }
        return "";
    }

    private static String jsonList(String json) {
        Object parsed;
        try {
            parsed = com.aresstack.enterpriseai.research.runtime.json.MiniJson.parse(json);
        } catch (RuntimeException invalid) {
            return shorten(json);
        }
        if (!(parsed instanceof List)) {
            return shorten(json);
        }
        StringBuilder text = new StringBuilder();
        for (Object element : (List<?>) parsed) {
            String value = element instanceof Map ? String.valueOf(firstValue((Map<?, ?>) element))
                    : String.valueOf(element);
            text.append(text.length() == 0 ? "" : ", ").append(value);
        }
        return shorten(text.toString());
    }

    private static Object firstValue(Map<?, ?> map) {
        for (Object key : new Object[]{"query", "name", "label", "text"}) {
            if (map.get(key) != null) {
                return map.get(key);
            }
        }
        return map.isEmpty() ? "" : map.values().iterator().next();
    }

    private static String arg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonEmpty(String first, String second) {
        return first.isEmpty() ? second : first;
    }

    private static String firstLine(String text) {
        String trimmed = text == null ? "" : text.trim();
        int end = trimmed.indexOf('\n');
        return end < 0 ? trimmed : trimmed.substring(0, end).trim();
    }

    static String shorten(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() > SHORT ? value.substring(0, SHORT - 1) + "…" : value;
    }
}
