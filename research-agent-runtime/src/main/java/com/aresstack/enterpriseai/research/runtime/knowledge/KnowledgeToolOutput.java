package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text answers of the host's knowledge tools ({@code knowledge_search}, {@code knowledge_read}). Shared by
 * every research path that talks to the knowledge tools through a {@code ToolInvoker}, so the wire format is parsed
 * in one place. Pure and stateless.
 */
public final class KnowledgeToolOutput {

    private static final Pattern HIT_HEADER = Pattern.compile("^\\[\\d+\\]\\s+(.*)$");
    /** Header lines of {@code knowledge_read} before the page text (metadata, never content). */
    private static final Pattern HEADER = Pattern.compile(
            "(?i)^(title|id|url|source|version|contenthash|from|passages|passage|note):.*$");
    /** The mark of a passage ({@code [passage <id>] heading}); the heading stays. */
    private static final Pattern PASSAGE_MARK = Pattern.compile("^\\[passage [^\\]]*\\]\\s*");

    private KnowledgeToolOutput() {
    }

    /**
     * The hits of {@code knowledge_search}: blocks {@code [n] title – section}, directly below the metadata lines
     * {@code id: …}, {@code url: …}, {@code passage: …}, {@code source: …} (only {@code id:} is mandatory), then the
     * snippet. Metadata never goes into the snippet. Also tolerates JSON (array or {@code hits}/{@code results} with
     * {@code id}, {@code title}, {@code snippet}, {@code url}).
     */
    public static List<KnowledgeHit> parseHits(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return fromJson(MiniJson.parse(trimmed));
            } catch (RuntimeException notJson) {
                // falls back to the line format
            }
        }
        List<KnowledgeHit> hits = new ArrayList<KnowledgeHit>();
        HitLines current = null;
        for (String line : trimmed.split("\\r?\\n")) {
            Matcher header = HIT_HEADER.matcher(line);
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

    /**
     * The page text of a {@code knowledge_read} answer without header lines and passage marks: only what is on the
     * page, so excerpts for the model carry no ids, hashes or versions. Without a header the text stays as it is.
     */
    public static String pageText(String readOutput) {
        if (readOutput == null) {
            return "";
        }
        String[] lines = readOutput.split("\\r?\\n", -1);
        int start = 0;
        while (start < lines.length && HEADER.matcher(lines[start].trim()).matches()) {
            start++;
        }
        StringBuilder text = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            text.append(PASSAGE_MARK.matcher(lines[i]).replaceFirst("")).append('\n');
        }
        return text.toString().trim();
    }

    /** The lines of one hit: first the metadata header, from the first other line on the snippet. */
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

    private static void addHit(List<KnowledgeHit> hits, HitLines lines) {
        if (lines != null && !lines.id.isEmpty()) {
            hits.add(new KnowledgeHit(lines.id, lines.title, lines.snippet.toString(), lines.url, lines.passage,
                    lines.source));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<KnowledgeHit> fromJson(Object parsed) {
        Object list = parsed;
        if (parsed instanceof Map) {
            Map<String, Object> object = (Map<String, Object>) parsed;
            list = object.containsKey("hits") ? object.get("hits") : object.get("results");
        }
        if (!(list instanceof List)) {
            return Collections.emptyList();
        }
        List<KnowledgeHit> hits = new ArrayList<KnowledgeHit>();
        for (Object element : (List<Object>) list) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<String, Object> hit = (Map<String, Object>) element;
            String id = first(hit, "id", "documentId", "resourceId");
            if (id.isEmpty()) {
                continue;
            }
            hits.add(new KnowledgeHit(id, first(hit, "title", "name"), first(hit, "snippet", "excerpt", "text"),
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
