package com.aresstack.enterpriseai.proasteion.help;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one help chapter: a small front matter block ({@code id}, {@code title}, {@code keywords}, optional
 * {@code since}), exactly one {@code #} title and sections per {@code ##} heading. Deliberately no YAML: one
 * {@code key: value} per line, keywords separated by commas. Stateless.
 */
public final class HelpMarkdown {

    private static final String FENCE = "---";
    private static final Pattern TOPIC_ID = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    /** Markdown link target {@code (help:area.topic#anchor)}; group 1 is the id, group 2 the optional anchor. */
    private static final Pattern HELP_LINK = Pattern.compile("\\]\\(" + Pattern.quote(HelpTopic.ID_PREFIX)
            + "([^)#\\s]+)(?:#([^)\\s]*))?\\)");

    private HelpMarkdown() {
    }

    /**
     * Parses {@code text} of the resource {@code resource} (relative name such as {@code chat/attachments.md}).
     *
     * @throws HelpFormatException if the chapter breaks the format
     */
    public static HelpTopic parse(String resource, String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("﻿")) {
            normalized = normalized.substring(1);
        }
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().equals(FENCE)) {
            throw new HelpFormatException(resource, "front matter missing (first line must be ---)");
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        int line = 1;
        for (; line < lines.length && !lines[line].trim().equals(FENCE); line++) {
            String entry = lines[line];
            if (entry.trim().isEmpty()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                throw new HelpFormatException(resource, "front matter line without key: " + entry);
            }
            fields.put(entry.substring(0, colon).trim().toLowerCase(Locale.ROOT), entry.substring(colon + 1).trim());
        }
        if (line >= lines.length) {
            throw new HelpFormatException(resource, "front matter not closed with ---");
        }
        String id = required(resource, fields, "id");
        if (!TOPIC_ID.matcher(id).matches()) {
            throw new HelpFormatException(resource, "invalid id '" + id + "' (expected area.topic)");
        }
        String title = required(resource, fields, "title");
        List<String> keywords = new ArrayList<String>();
        String rawKeywords = fields.containsKey("keywords") ? fields.get("keywords") : "";
        for (String keyword : rawKeywords.split(",")) {
            if (!keyword.trim().isEmpty()) {
                keywords.add(keyword.trim());
            }
        }
        String since = fields.get("since");
        if (since != null && !since.isEmpty() && !VERSION.matcher(since).matches()) {
            throw new HelpFormatException(resource, "since must be MAJOR.MINOR.PATCH, is '" + since + "'");
        }
        List<HelpSection> sections = sections(resource, title, lines, line + 1);
        return new HelpTopic(id, title, keywords, since == null || since.isEmpty() ? null : since, resource,
                sections);
    }

    private static List<HelpSection> sections(String resource, String title, String[] lines, int start) {
        String heading = null;
        boolean titleSeen = false;
        StringBuilder body = new StringBuilder();
        List<HelpSection> sections = new ArrayList<HelpSection>();
        boolean inCode = false;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
            }
            if (!inCode && line.startsWith("# ")) {
                if (titleSeen) {
                    throw new HelpFormatException(resource, "more than one # title");
                }
                if (!line.substring(2).trim().equals(title)) {
                    throw new HelpFormatException(resource, "# title differs from front matter title");
                }
                titleSeen = true;
                continue;
            }
            if (!inCode && line.startsWith("## ")) {
                add(sections, heading, body);
                heading = line.substring(3).trim();
                body.setLength(0);
                continue;
            }
            body.append(line).append('\n');
        }
        add(sections, heading, body);
        if (!titleSeen) {
            throw new HelpFormatException(resource, "# title missing");
        }
        return sections;
    }

    private static void add(List<HelpSection> sections, String heading, StringBuilder body) {
        String markdown = body.toString().trim();
        if (heading == null) {
            if (!markdown.isEmpty()) {
                sections.add(new HelpSection("", "", markdown));
            }
            return;
        }
        sections.add(new HelpSection(slug(heading), heading, markdown));
    }

    private static String required(String resource, Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isEmpty()) {
            throw new HelpFormatException(resource, "front matter field '" + key + "' missing");
        }
        return value;
    }

    /**
     * Anchor of a heading: lower case, umlauts folded ({@code ä → ae}, {@code ß → ss}), every other character
     * outside {@code a-z0-9} becomes {@code -}, runs collapsed, edges trimmed.
     */
    public static String slug(String heading) {
        String folded = HelpText.fold(heading);
        StringBuilder slug = new StringBuilder(folded.length());
        boolean dash = false;
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                slug.append(c);
                dash = false;
            } else if (!dash && slug.length() > 0) {
                slug.append('-');
                dash = true;
            }
        }
        int end = slug.length();
        while (end > 0 && slug.charAt(end - 1) == '-') {
            end--;
        }
        return slug.substring(0, end);
    }

    /** Every {@code help:} link target in {@code markdown} as {@code id} or {@code id#anchor}. */
    public static List<String> helpLinks(String markdown) {
        List<String> links = new ArrayList<String>();
        Matcher matcher = HELP_LINK.matcher(markdown);
        while (matcher.find()) {
            links.add(matcher.group(2) == null ? matcher.group(1) : matcher.group(1) + "#" + matcher.group(2));
        }
        return links;
    }
}
