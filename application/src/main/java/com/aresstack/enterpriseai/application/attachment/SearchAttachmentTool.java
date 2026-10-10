package com.aresstack.enterpriseai.application.attachment;

import com.aresstack.enterpriseai.application.tool.AiTool;
import com.aresstack.enterpriseai.application.tool.ToolArguments;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code search_attachment}: einfache Treffersuche in Anhängen; liefert die Absätze, die den Suchbegriff
 * enthalten (ohne Groß-/Kleinschreibung), gekürzt und mit Absatznummer.
 */
public final class SearchAttachmentTool implements AiTool {

    public static final String NAME = "search_attachment";
    static final int MAX_HITS = 20;
    static final int MAX_HIT_CHARS = 600;

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"query\":{\"type\":\"string\",\"description\":\"Suchbegriff oder Wortfolge\"},"
            + "\"attachment_id\":{\"type\":\"string\",\"description\":\"Kennung des Anhangs; ohne Angabe alle Anhänge\"}},"
            + "\"required\":[\"query\"]}";

    private final AttachmentTexts texts;

    SearchAttachmentTool(AttachmentTexts texts) {
        this.texts = texts;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(NAME, "Sucht einen Begriff in den angehängten Dateien und liefert die "
                + "passenden Absätze. Für gezielte Fragen zu großen Dateien.", SCHEMA);
    }

    @Override
    public String execute(String arguments) {
        ToolArguments args = ToolArguments.parse(arguments);
        String query = args.required("query").trim();
        String id = args.text("attachment_id");
        List<Attachment> attachments = id == null ? texts.list() : Collections.singletonList(texts.find(id));
        String needle = query.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int hits = 0;
        for (Attachment attachment : attachments) {
            String[] paragraphs = texts.text(attachment).split("\\n\\s*\\n|\\r?\\n");
            for (int i = 0; i < paragraphs.length && hits < MAX_HITS; i++) {
                String paragraph = paragraphs[i].trim();
                int at = paragraph.toLowerCase(Locale.ROOT).indexOf(needle);
                if (at < 0) {
                    continue;
                }
                hits++;
                out.append(attachment.id()).append(" (").append(attachment.fileName()).append("), Absatz ")
                        .append(i + 1).append(": ").append(excerpt(paragraph, at)).append("\n\n");
            }
        }
        if (hits == 0) {
            return "Keine Treffer für \"" + query + "\".";
        }
        return hits + (hits >= MAX_HITS ? "+" : "") + " Treffer für \"" + query + "\":\n\n" + out.toString().trim();
    }

    private static String excerpt(String paragraph, int at) {
        if (paragraph.length() <= MAX_HIT_CHARS) {
            return paragraph;
        }
        int start = Math.max(0, at - MAX_HIT_CHARS / 3);
        int end = Math.min(paragraph.length(), start + MAX_HIT_CHARS);
        return (start > 0 ? "…" : "") + paragraph.substring(start, end) + (end < paragraph.length() ? "…" : "");
    }
}
