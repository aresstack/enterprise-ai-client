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
     * The diagnostic {@code Note:} lines of a {@code knowledge_search} answer (a source or the index could not be
     * searched): outages, never evidence and never to be mistaken for "no hits".
     */
    public static List<String> notes(String text) {
        List<String> notes = new ArrayList<String>();
        if (text == null) {
            return notes;
        }
        for (String line : text.split("\\r?\\n")) {
            if (line.startsWith("Note: ")) {
                notes.add(line.substring("Note: ".length()).trim());
            }
        }
        return notes;
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

    /**
     * The value of one header line ({@code title}, {@code url}, ...) of a {@code knowledge_read} answer; empty when
     * the header has no such line.
     */
    public static String header(String readOutput, String key) {
        if (readOutput == null) {
            return "";
        }
        String prefix = key.toLowerCase(java.util.Locale.ROOT) + ":";
        for (String line : readOutput.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!HEADER.matcher(trimmed).matches()) {
                break;
            }
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    /** One page of a {@code knowledge_tree} answer: title, resource id and bookmark (both may be empty). */
    public static final class TreeNode {
        public final String title;
        public final String id;
        public final String bookmark;

        TreeNode(String title, String id, String bookmark) {
            this.title = title == null ? "" : title;
            this.id = id == null ? "" : id;
            this.bookmark = bookmark == null ? "" : bookmark;
        }

        @Override
        public String toString() {
            return title + " (" + id + ")";
        }
    }

    /**
     * The neighbourhood of a {@code knowledge_tree} answer: the source, the parent pages up to the space (top first,
     * space entries without a page id left out), the current page ({@code null} at a space root) and the direct
     * children. Only pages with an id are kept, because only those can be read.
     */
    public static final class TreeNeighbourhood {
        public final String source;
        public final List<TreeNode> parents;
        public final TreeNode current;
        public final List<TreeNode> children;

        TreeNeighbourhood(String source, List<TreeNode> parents, TreeNode current, List<TreeNode> children) {
            this.source = source;
            this.parents = Collections.unmodifiableList(parents);
            this.current = current;
            this.children = Collections.unmodifiableList(children);
        }
    }

    /**
     * Parses a {@code knowledge_tree} answer: the sections {@code parents:}, {@code current:} and {@code children:},
     * each entry with {@code title:}, {@code id:} and {@code bookmark:} lines. Lines outside the sections (cursor,
     * hints) are ignored.
     */
    public static TreeNeighbourhood parseTree(String text) {
        String source = "";
        List<TreeNode> parents = new ArrayList<TreeNode>();
        List<TreeNode> children = new ArrayList<TreeNode>();
        TreeNode current = null;
        String section = "";
        String[] entry = null; // title, id, bookmark of the entry being read
        for (String line : (text == null ? "" : text).split("\\r?\\n")) {
            boolean indented = line.startsWith("  ");
            boolean item = line.startsWith("- ");
            if (!indented && !item) {
                List<TreeNode> target = "parents".equals(section) ? parents : children;
                current = finish(entry, section, target, current);
                entry = null;
                String trimmed = line.trim();
                if (trimmed.startsWith("parents:")) {
                    section = "parents";
                } else if (trimmed.equals("current:")) {
                    section = "current";
                    entry = new String[]{"", "", ""};
                } else if (trimmed.equals("children:")) {
                    section = "children";
                } else if (section.isEmpty() && trimmed.startsWith("source: ")) {
                    source = trimmed.substring("source: ".length()).trim();
                } else if (!trimmed.isEmpty()) {
                    section = ""; // cursor lines and hints end the sections
                }
                continue;
            }
            if (section.isEmpty()) {
                continue;
            }
            if (item) {
                current = finish(entry, section, "parents".equals(section) ? parents : children, current);
                entry = new String[]{"", "", ""};
            }
            if (entry == null) {
                continue;
            }
            String field = line.substring(2).trim();
            if (field.startsWith("title: ")) {
                entry[0] = field.substring("title: ".length()).trim();
            } else if (field.startsWith("id: ")) {
                entry[1] = field.substring("id: ".length()).trim();
            } else if (field.startsWith("bookmark: ")) {
                entry[2] = field.substring("bookmark: ".length()).trim();
            }
        }
        current = finish(entry, section, "parents".equals(section) ? parents : children, current);
        return new TreeNeighbourhood(source, parents, current, children);
    }

    /** Stores a finished entry in its section; returns the (possibly new) current page. */
    private static TreeNode finish(String[] entry, String section, List<TreeNode> target, TreeNode current) {
        if (entry == null || entry[1].isEmpty()) {
            return current;
        }
        TreeNode node = new TreeNode(entry[0], entry[1], entry[2]);
        if ("current".equals(section)) {
            return node;
        }
        target.add(node);
        return current;
    }

    private static final Pattern READ_MANY_FRAME = Pattern.compile("^\\[(\\d+)/(\\d+)\\] (.*)$");
    private static final String READ_MANY_SEPARATOR = "\n\n---\n\n";

    /**
     * The read texts of a {@code knowledge_read_many} answer by position of the requested id (0-based): each frame
     * {@code [i/n] OK} (or {@code OK (truncated ...)}) is followed by the {@code knowledge_read} answer of that id.
     * Failed, omitted and duplicate positions are absent. A separator inside a page text stays part of that text.
     */
    public static Map<Integer, String> readManyTexts(String text) {
        Map<Integer, String> texts = new java.util.LinkedHashMap<Integer, String>();
        if (text == null) {
            return texts;
        }
        List<String> frames = new ArrayList<String>();
        for (String chunk : text.split(Pattern.quote(READ_MANY_SEPARATOR), -1)) {
            if (frames.isEmpty() || READ_MANY_FRAME.matcher(firstLine(chunk)).matches()) {
                frames.add(chunk);
            } else {
                int last = frames.size() - 1;
                frames.set(last, frames.get(last) + READ_MANY_SEPARATOR + chunk);
            }
        }
        for (String frame : frames) {
            Matcher header = READ_MANY_FRAME.matcher(firstLine(frame));
            if (!header.matches()) {
                continue;
            }
            String status = header.group(3).trim();
            if (!status.equals("OK") && !status.startsWith("OK (truncated")) {
                continue;
            }
            int newline = frame.indexOf('\n');
            String body = newline < 0 ? "" : frame.substring(newline + 1).trim();
            if (!body.isEmpty()) {
                texts.put(Integer.valueOf(Integer.parseInt(header.group(1)) - 1), body);
            }
        }
        return texts;
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).trim();
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
