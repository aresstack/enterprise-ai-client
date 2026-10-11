package com.aresstack.enterpriseai.proasteion.application.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Research-Dokument eines Projekts ({@code document.json}): Titel, Gliederung, ausführlicher Text je Abschnitt,
 * die Quellen mit Nummer und die Zuordnung der belegten Aussagen zu Quellen und Passagen. Im Text stehen Belege als
 * {@code [n]}; {@code n} ist die Nummer einer übernommenen Quelle. Unveränderlich.
 */
public final class ResearchDocument {

    /** Ein Abschnitt mit Überschrift und Text (Belege als {@code [n]}). */
    public static final class Section {
        private final String heading;
        private final String text;

        public Section(String heading, String text) {
            this.heading = heading == null ? "" : heading.trim();
            this.text = text == null ? "" : text.trim();
        }

        public String heading() {
            return heading;
        }

        public String text() {
            return text;
        }
    }

    /** Eine zitierte Quelle mit ihrer Nummer im Dokument. */
    public static final class Source {
        private final int number;
        private final String resourceId;
        private final String title;
        private final String url;

        public Source(int number, String resourceId, String title, String url) {
            this.number = number;
            this.resourceId = resourceId == null ? "" : resourceId;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }

        public int number() {
            return number;
        }

        public String resourceId() {
            return resourceId;
        }

        public String title() {
            return title.isEmpty() ? resourceId : title;
        }

        public String url() {
            return url;
        }
    }

    /** Eine belegte Aussage: der Satz, seine Quellen (Nummern) und Passagen (IDs aus dem Wissensindex). */
    public static final class Claim {
        private final String text;
        private final List<Integer> sources;
        private final List<String> passages;

        public Claim(String text, List<Integer> sources, List<String> passages) {
            this.text = text == null ? "" : text.trim();
            this.sources = Collections.unmodifiableList(new ArrayList<Integer>(sources));
            this.passages = Collections.unmodifiableList(new ArrayList<String>(passages));
        }

        public String text() {
            return text;
        }

        public List<Integer> sources() {
            return sources;
        }

        public List<String> passages() {
            return passages;
        }
    }

    private static final Pattern PASSAGE_MARK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    private static final Pattern NUMBER_MARK = Pattern.compile("\\[(\\d+)\\]");

    private final String title;
    private final String outline;
    private final List<Section> sections;
    private final List<Source> sources;
    private final List<Claim> claims;

    public ResearchDocument(String title, String outline, List<Section> sections, List<Source> sources,
                            List<Claim> claims) {
        this.title = title == null ? "" : title.trim();
        this.outline = outline == null ? "" : outline.trim();
        this.sections = Collections.unmodifiableList(new ArrayList<Section>(sections));
        this.sources = Collections.unmodifiableList(new ArrayList<Source>(sources));
        this.claims = Collections.unmodifiableList(new ArrayList<Claim>(claims));
    }

    public String title() {
        return title;
    }

    public String outline() {
        return outline;
    }

    public List<Section> sections() {
        return sections;
    }

    public List<Source> sources() {
        return sources;
    }

    public List<Claim> claims() {
        return claims;
    }

    /** Die Quelle mit dieser Nummer oder {@code null}. */
    public Source source(int number) {
        for (Source source : sources) {
            if (source.number() == number) {
                return source;
            }
        }
        return null;
    }

    /**
     * Baut das Dokument aus dem Entwurf des Agenten: Abschnitte mit Passagen-Markern {@code [[passage-id]]} und der
     * Zuordnung Passage → Ressource. Nur übernommene Quellen werden nummeriert (Reihenfolge des ersten Belegs);
     * ein Marker auf eine fremde Ressource entfällt, damit jede Aussage auf eine gespeicherte Quelle zurückführt.
     */
    static ResearchDocument fromDraft(String title, String outline, List<Section> drafted,
                                      Map<String, String> resourceByPassage, List<ResearchSourceRef> accepted) {
        Map<String, ResearchSourceRef> acceptedById = new LinkedHashMap<String, ResearchSourceRef>();
        for (ResearchSourceRef source : accepted) {
            acceptedById.put(source.resourceId(), source);
        }
        Map<String, Integer> numbers = new LinkedHashMap<String, Integer>();
        List<Section> sections = new ArrayList<Section>();
        List<Claim> claims = new ArrayList<Claim>();
        for (Section section : drafted) {
            StringBuilder text = new StringBuilder();
            for (String sentence : sentences(section.text())) {
                List<Integer> sentenceSources = new ArrayList<Integer>();
                List<String> sentencePassages = new ArrayList<String>();
                Matcher marks = PASSAGE_MARK.matcher(sentence);
                StringBuffer rewritten = new StringBuffer();
                while (marks.find()) {
                    String passage = marks.group(1).trim();
                    String resource = resourceOf(passage, resourceByPassage);
                    String replacement = "";
                    if (resource != null && acceptedById.containsKey(resource)) {
                        Integer number = numbers.get(resource);
                        if (number == null) {
                            number = numbers.size() + 1;
                            numbers.put(resource, number);
                        }
                        if (!sentenceSources.contains(number)) {
                            sentenceSources.add(number);
                            replacement = "[" + number + "]";
                        }
                        sentencePassages.add(passage);
                    }
                    marks.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
                }
                marks.appendTail(rewritten);
                String clean = rewritten.toString().replaceAll("[ \\t]+([.,;:!?])", "$1").replaceAll("[ \\t]{2,}", " ");
                if (!sentenceSources.isEmpty()) {
                    claims.add(new Claim(NUMBER_MARK.matcher(clean).replaceAll("").trim(), sentenceSources,
                            sentencePassages));
                }
                text.append(clean);
            }
            sections.add(new Section(section.heading(), text.toString()));
        }
        List<Source> sources = new ArrayList<Source>();
        for (Map.Entry<String, Integer> number : numbers.entrySet()) {
            ResearchSourceRef ref = acceptedById.get(number.getKey());
            sources.add(new Source(number.getValue(), ref.resourceId(), ref.title(), ref.url()));
        }
        return new ResearchDocument(title, outline, sections, sources, claims);
    }

    /** Die Ressource einer Passage: aus der Zuordnung, sonst der Teil vor {@code @} bzw. {@code #}. */
    private static String resourceOf(String passage, Map<String, String> resourceByPassage) {
        String mapped = resourceByPassage == null ? null : resourceByPassage.get(passage);
        if (mapped != null && !mapped.trim().isEmpty()) {
            return mapped.trim();
        }
        int at = passage.indexOf('@');
        int hash = passage.indexOf('#');
        int end = at >= 0 && (hash < 0 || at < hash) ? at : hash;
        return end > 0 ? passage.substring(0, end) : passage;
    }

    /**
     * Sätze samt ihrem folgenden Leerraum (zusammengefügt ergibt sich der Text unverändert). Ein Satz endet nach
     * {@code .!?} oder einem Zeilenumbruch; Marker direkt hinter dem Satzzeichen gehören noch zu ihm.
     */
    static List<String> sentences(String text) {
        List<String> sentences = new ArrayList<String>();
        int start = 0;
        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                int end = i + 1;
                while (end < length && (text.charAt(end) == '.' || text.charAt(end) == '!'
                        || text.charAt(end) == '?')) {
                    end++;
                }
                // Marker hinter dem Satzzeichen („… erreicht. [[p1]]“) gehören zum Satz.
                int probe = end;
                while (true) {
                    int spaces = probe;
                    while (spaces < length && text.charAt(spaces) == ' ') {
                        spaces++;
                    }
                    if (text.startsWith("[[", spaces)) {
                        int close = text.indexOf("]]", spaces);
                        if (close < 0) {
                            break;
                        }
                        probe = close + 2;
                        end = probe;
                    } else {
                        break;
                    }
                }
                while (end < length && Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                sentences.add(text.substring(start, end));
                start = end;
                i = end;
            } else {
                i++;
            }
        }
        if (start < length) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    // ------------------------------------------------------------------ JSON

    String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("title", title);
        root.addProperty("outline", outline);
        JsonArray sectionArray = new JsonArray();
        for (Section section : sections) {
            JsonObject object = new JsonObject();
            object.addProperty("heading", section.heading());
            object.addProperty("text", section.text());
            sectionArray.add(object);
        }
        root.add("sections", sectionArray);
        JsonArray sourceArray = new JsonArray();
        for (Source source : sources) {
            JsonObject object = new JsonObject();
            object.addProperty("number", source.number());
            object.addProperty("resourceId", source.resourceId());
            object.addProperty("title", source.title());
            object.addProperty("url", source.url());
            sourceArray.add(object);
        }
        root.add("sources", sourceArray);
        JsonArray claimArray = new JsonArray();
        for (Claim claim : claims) {
            JsonObject object = new JsonObject();
            object.addProperty("text", claim.text());
            JsonArray numbers = new JsonArray();
            for (Integer number : claim.sources()) {
                numbers.add(number);
            }
            object.add("sources", numbers);
            JsonArray passages = new JsonArray();
            for (String passage : claim.passages()) {
                passages.add(passage);
            }
            object.add("passages", passages);
            claimArray.add(object);
        }
        root.add("claims", claimArray);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(root);
    }

    static ResearchDocument fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Section> sections = new ArrayList<Section>();
        for (JsonElement element : array(root, "sections")) {
            JsonObject object = element.getAsJsonObject();
            sections.add(new Section(text(object, "heading"), text(object, "text")));
        }
        List<Source> sources = new ArrayList<Source>();
        for (JsonElement element : array(root, "sources")) {
            JsonObject object = element.getAsJsonObject();
            sources.add(new Source(object.has("number") ? object.get("number").getAsInt() : 0,
                    text(object, "resourceId"), text(object, "title"), text(object, "url")));
        }
        List<Claim> claims = new ArrayList<Claim>();
        for (JsonElement element : array(root, "claims")) {
            JsonObject object = element.getAsJsonObject();
            List<Integer> numbers = new ArrayList<Integer>();
            for (JsonElement number : array(object, "sources")) {
                numbers.add(number.getAsInt());
            }
            List<String> passages = new ArrayList<String>();
            for (JsonElement passage : array(object, "passages")) {
                passages.add(passage.getAsString());
            }
            claims.add(new Claim(text(object, "text"), numbers, passages));
        }
        return new ResearchDocument(text(root, "title"), text(root, "outline"), sections, sources, claims);
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    /** Das Dokument als Markdown (Titel, Abschnitte, Quellenverzeichnis), z. B. für den Agenten bei Nachfragen. */
    public String toMarkdown() {
        StringBuilder text = new StringBuilder("# ").append(title).append("\n");
        for (Section section : sections) {
            text.append("\n## ").append(section.heading()).append("\n\n").append(section.text()).append("\n");
        }
        if (!sources.isEmpty()) {
            text.append("\n## Quellen\n\n");
            for (Source source : sources) {
                text.append("- [").append(source.number()).append("] ").append(source.title());
                if (!source.url().isEmpty()) {
                    text.append(" – ").append(source.url());
                }
                text.append('\n');
            }
        }
        return text.toString();
    }
}
