package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.document.MarkdownDocxRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vladsch.flexmark.ast.Text;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schreibt den (bereits geschwärzten) Exportbaum von {@link ChatExport#export(String)} als Word-Dokument: ein
 * Gesprächsbericht statt Sprechblasen. Jede Nachricht bekommt einen Autorenkopf („Du · 13:31“, bei Antworten der
 * Anzeigename aus dem Export wie „Merlins Tochter · 13:32“, „Hinweis · 13:33“) in dezenter Akzentfarbe, ihr Markdown wird strukturell zu Word (Überschriften als
 * Überschriftenformate, Listen als Word-Listen, Tabellen als Word-Tabellen, Links als Hyperlinks), darunter stehen
 * Anhänge, Seiten, Verweise, Zitate und Quellen. Werkzeugaufrufe bleiben dem JSON-Export vorbehalten. Am Ende fasst
 * „Ressourcen des Chats“ Dateien, Seiten und Quellen chatweit dedupliziert zusammen.
 *
 * <p>Nur Darstellung: Sammeln und Schwärzen bleiben bei {@link ChatExport}; dieser Schreiber sieht nie den
 * ungeschwärzten Chat. Unbekannte Markdown-Knoten behalten ihren lesbaren Text, Mermaid und SVG erscheinen als
 * Codeblock.
 */
public final class ChatDocxWriter {

    static final String STYLE_TITLE = MarkdownDocxRenderer.STYLE_TITLE;
    static final String STYLE_HEADING = MarkdownDocxRenderer.STYLE_HEADING;
    static final String USER_COLOR = MarkdownDocxRenderer.LINK_COLOR;
    static final String ASSISTANT_COLOR = "0E7C7B";
    static final String NOTICE_COLOR = "6B6B6B";

    private static final String MUTED_COLOR = MarkdownDocxRenderer.MUTED_COLOR;
    private static final int SMALL_POINTS = MarkdownDocxRenderer.SMALL_POINTS;
    private static final int INDENT_STEP = MarkdownDocxRenderer.INDENT_STEP;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private final ZoneId zone;

    /** @param zone Zeitzone für Uhrzeiten und Datum im Dokument */
    public ChatDocxWriter(ZoneId zone) {
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
    }

    /** Das Word-Dokument zum Exportbaum. */
    public byte[] write(JsonObject export) throws IOException {
        XWPFDocument document = new XWPFDocument();
        try {
            new Builder(document).build(export);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }

    // ------------------------------------------------------------------ Aufbau eines Dokuments

    private final class Builder {

        private final XWPFDocument document;
        private final MarkdownDocxRenderer renderer;
        /** Kopf der Antworten: Anzeigename aus dem Export (die Rolle bleibt ASSISTANT). */
        private String assistantTitle = ChatExport.DEFAULT_ASSISTANT_TITLE;

        Builder(XWPFDocument document) {
            this.document = document;
            this.renderer = new MarkdownDocxRenderer(document);
        }

        void build(JsonObject export) {
            assistantTitle = ChatExport.assistantTitle(export);
            JsonObject chat = object(export, "chat");
            String title = string(chat, "title");
            if (title.isEmpty()) {
                title = "Chat";
            }
            document.getProperties().getCoreProperties().setTitle(title);
            document.getProperties().getCoreProperties().setIdentifier(string(chat, "id"));
            document.getProperties().getCoreProperties().setCreator(string(export, "application").isEmpty()
                    ? "Enterprise AI Client" : "Enterprise AI Client " + string(export, "application"));

            XWPFParagraph heading = paragraph();
            heading.setStyle(STYLE_TITLE);
            heading.createRun().setText(clean(title));
            OffsetDateTime exported = time(string(export, "exportedAt"));
            if (exported != null) {
                XWPFParagraph line = paragraph();
                line.setSpacingAfter(240);
                small(line, "Exportiert am " + DATE.format(exported) + " um " + TIME.format(exported), MUTED_COLOR);
            }

            Resources resources = new Resources();
            String lastDate = null;
            JsonArray messages = array(chat, "messages");
            if (messages != null) {
                for (JsonElement element : messages) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject message = element.getAsJsonObject();
                    OffsetDateTime at = time(string(message, "timestamp"));
                    String date = at == null ? null : DATE.format(at);
                    if (date != null && !date.equals(lastDate)) {
                        XWPFParagraph day = paragraph();
                        day.setSpacingBefore(240);
                        small(day, date, MUTED_COLOR);
                        lastDate = date;
                    }
                    message(message, at);
                    resources.collect(message);
                }
            }
            JsonElement agent = export.get("agent");
            if (agent != null && agent.isJsonObject()) {
                resources.collectAgent(agent.getAsJsonObject());
            }
            resources.render();
        }

        // -------------------------------------------------------------- Nachrichten

        private void message(JsonObject message, OffsetDateTime at) {
            String author = string(message, "author");
            String name = "USER".equals(author) ? "Du" : "ASSISTANT".equals(author) ? assistantTitle
                    : "NOTICE".equals(author) ? "Hinweis" : author;
            String color = "USER".equals(author) ? USER_COLOR : "ASSISTANT".equals(author) ? ASSISTANT_COLOR
                    : NOTICE_COLOR;
            XWPFParagraph head = paragraph();
            head.setSpacingBefore(280);
            head.setSpacingAfter(120);
            head.setKeepNext(true);
            head.setBorderBottom(Borders.SINGLE);
            borderColor(head, color);
            XWPFRun who = head.createRun();
            who.setBold(true);
            who.setColor(color);
            who.setText(name);
            StringBuilder rest = new StringBuilder();
            if (at != null) {
                rest.append(" · ").append(TIME.format(at));
            }
            if (!string(message, "phase").isEmpty()) {
                rest.append(" · Phase ").append(string(message, "phase"));
            }
            if ("CANCELLED".equals(string(message, "state"))) {
                rest.append(" · abgebrochen");
            }
            if (rest.length() > 0) {
                XWPFRun meta = head.createRun();
                meta.setColor(MUTED_COLOR);
                meta.setText(rest.toString());
            }

            markdown(string(message, "text"), "NOTICE".equals(author));

            List<String> attachments = new ArrayList<String>();
            for (JsonObject attachment : objects(message, "attachments")) {
                attachments.add(string(attachment, "fileName"));
            }
            bulletSection("Anhänge", attachments);

            List<String[]> pages = new ArrayList<String[]>();
            for (JsonObject bookmark : objects(message, "bookmarks")) {
                pages.add(new String[] {firstNonEmpty(string(bookmark, "title"), string(bookmark, "uri")),
                        string(bookmark, "uri")});
            }
            linkSection("Verwendete Seiten", pages);

            List<String> references = new ArrayList<String>();
            for (JsonObject reference : objects(message, "references")) {
                references.add(firstNonEmpty(string(reference, "title"), string(reference, "id")));
            }
            bulletSection("Verweise", references);

            List<String> quotes = new ArrayList<String>();
            for (JsonObject quote : objects(message, "quotes")) {
                String text = string(quote, "text").trim().replaceAll("\\s+", " ");
                if (!text.isEmpty()) {
                    quotes.add("„" + text + "“");
                }
            }
            bulletSection("Zitate", quotes);

            List<JsonObject> sources = objects(message, "sources");
            if (!sources.isEmpty()) {
                sectionLabel("Quellen");
                for (JsonObject source : sources) {
                    XWPFParagraph line = paragraph();
                    line.setSpacingAfter(0);
                    line.setIndentationLeft(INDENT_STEP);
                    line.setIndentationHanging(INDENT_STEP);
                    XWPFRun number = line.createRun();
                    number.setColor(MUTED_COLOR);
                    number.setText("[" + string(source, "number") + "] ");
                    String location = string(source, "location");
                    if (isWebLink(location)) {
                        hyperlink(line, firstNonEmpty(string(source, "title"), location), location, false);
                    } else {
                        line.createRun().setText(clean(string(source, "title")));
                    }
                    detail("Abschnitt", string(source, "heading"));
                    detail("Ort", location);
                    detail("Stand", string(source, "revision"));
                }
            }
        }

        private void detail(String label, String value) {
            if (value.isEmpty()) {
                return;
            }
            XWPFParagraph line = paragraph();
            line.setSpacingAfter(0);
            line.setIndentationLeft(2 * INDENT_STEP);
            small(line, label + ": " + value, MUTED_COLOR);
        }

        private void sectionLabel(String label) {
            XWPFParagraph paragraph = paragraph();
            paragraph.setSpacingBefore(120);
            paragraph.setSpacingAfter(40);
            paragraph.setKeepNext(true);
            XWPFRun run = paragraph.createRun();
            run.setBold(true);
            run.setFontSize(SMALL_POINTS);
            run.setColor(MUTED_COLOR);
            run.setText(label);
        }

        private void bulletSection(String label, List<String> items) {
            List<String> present = new ArrayList<String>();
            for (String item : items) {
                if (item != null && !item.trim().isEmpty()) {
                    present.add(item);
                }
            }
            if (present.isEmpty()) {
                return;
            }
            sectionLabel(label);
            for (String item : present) {
                XWPFParagraph line = renderer.bulletParagraph(0);
                line.createRun().setText(clean(item));
            }
        }

        private void linkSection(String label, List<String[]> items) {
            if (items.isEmpty()) {
                return;
            }
            sectionLabel(label);
            for (String[] item : items) {
                XWPFParagraph line = renderer.bulletParagraph(0);
                if (isWebLink(item[1])) {
                    hyperlink(line, item[0], item[1], false);
                } else {
                    line.createRun().setText(clean(item[0]));
                    if (!item[1].isEmpty() && !item[1].equals(item[0])) {
                        XWPFRun uri = line.createRun();
                        uri.setColor(MUTED_COLOR);
                        uri.setText(" · " + clean(item[1]));
                    }
                }
            }
        }

        // -------------------------------------------------------------- Ressourcen des Chats

        private final class Resources {
            private final Map<String, String> files = new LinkedHashMap<String, String>();
            private final Map<String, String[]> pages = new LinkedHashMap<String, String[]>();
            private final Map<String, String[]> sources = new LinkedHashMap<String, String[]>();

            void collect(JsonObject message) {
                for (JsonObject attachment : objects(message, "attachments")) {
                    String name = string(attachment, "fileName");
                    String key = firstNonEmpty(string(attachment, "id"), name);
                    if (!key.isEmpty() && !files.containsKey(key)) {
                        files.put(key, name);
                    }
                }
                for (JsonObject bookmark : objects(message, "bookmarks")) {
                    addPage(string(bookmark, "uri"), string(bookmark, "title"));
                }
                for (JsonObject source : objects(message, "sources")) {
                    String location = string(source, "location");
                    String key = firstNonEmpty(string(source, "resourceId"), location, string(source, "title"));
                    if (!key.isEmpty() && !sources.containsKey(key)) {
                        sources.put(key, new String[] {firstNonEmpty(string(source, "title"), location), location});
                    }
                }
            }

            void collectAgent(JsonObject agent) {
                for (JsonObject source : objects(agent, "sources")) {
                    String url = string(source, "url");
                    String key = firstNonEmpty(string(source, "id"), url, string(source, "title"));
                    if (!key.isEmpty() && !sources.containsKey(key)) {
                        sources.put(key, new String[] {firstNonEmpty(string(source, "title"), url), url});
                    }
                }
            }

            private void addPage(String uri, String title) {
                String key = firstNonEmpty(uri, title);
                if (!key.isEmpty() && !pages.containsKey(key)) {
                    pages.put(key, new String[] {firstNonEmpty(title, uri), uri});
                }
            }

            void render() {
                if (files.isEmpty() && pages.isEmpty() && sources.isEmpty()) {
                    return;
                }
                XWPFParagraph heading = paragraph();
                heading.setStyle(STYLE_HEADING + 1);
                heading.setSpacingBefore(480);
                heading.createRun().setText("Ressourcen des Chats");
                bulletSection("Dateien", new ArrayList<String>(files.values()));
                linkSection("Seiten", new ArrayList<String[]>(pages.values()));
                linkSection("Verwendete Quellen", new ArrayList<String[]>(sources.values()));
            }
        }

        // -------------------------------------------------------------- Bausteine

        private void markdown(String text, boolean muted) {
            renderer.markdown(text, muted);
        }

        private XWPFParagraph paragraph() {
            return renderer.paragraph();
        }

        private void small(XWPFParagraph paragraph, String text, String color) {
            renderer.small(paragraph, text, color);
        }

        private XWPFRun hyperlink(XWPFParagraph paragraph, String label, String url, boolean bold) {
            return renderer.hyperlink(paragraph, label, url, bold);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static void borderColor(XWPFParagraph paragraph, String color) {
        MarkdownDocxRenderer.borderColor(paragraph, color);
    }

    static boolean isWebLink(String url) {
        return MarkdownDocxRenderer.isWebLink(url);
    }

    static String clean(String text) {
        return MarkdownDocxRenderer.clean(text);
    }

    private OffsetDateTime time(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(zone).toOffsetDateTime();
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static List<JsonObject> objects(JsonObject parent, String key) {
        List<JsonObject> result = new ArrayList<JsonObject>();
        JsonArray values = array(parent, key);
        if (values != null) {
            for (JsonElement value : values) {
                if (value != null && value.isJsonObject()) {
                    result.add(value.getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }
}
