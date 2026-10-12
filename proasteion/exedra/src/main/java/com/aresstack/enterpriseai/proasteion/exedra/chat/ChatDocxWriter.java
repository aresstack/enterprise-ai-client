package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlCommentBlock;
import com.vladsch.flexmark.ast.HtmlInlineComment;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.RefNode;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
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

    static final String STYLE_TITLE = "Title";
    static final String STYLE_HEADING = "Heading";
    static final String USER_COLOR = "1F5FAF";
    static final String ASSISTANT_COLOR = "0E7C7B";
    static final String NOTICE_COLOR = "6B6B6B";

    private static final String MUTED_COLOR = "6B6B6B";
    private static final String RULE_COLOR = "BFC7D0";
    private static final String CODE_FONT = "Consolas";
    private static final String BODY_FONT = "Calibri";
    private static final String HEADER_FILL = "EAF0F6";
    private static final String CODE_FILL = "F4F5F7";
    private static final int BODY_HALF_POINTS = 22;
    private static final int SMALL_POINTS = 9;
    private static final int CODE_POINTS = 9;
    private static final int INDENT_STEP = 360;
    private static final int MAX_LEVELS = 9;
    private static final List<String> BULLETS = java.util.Collections.unmodifiableList(
            Arrays.asList("•", "◦", "▪"));
    private static final int[] HEADING_HALF_POINTS = {32, 28, 26, 24, 22, 22};

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private final ZoneId zone;
    private final Parser parser;

    /** @param zone Zeitzone für Uhrzeiten und Datum im Dokument */
    public ChatDocxWriter(ZoneId zone) {
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(),
                AutolinkExtension.create()));
        this.parser = Parser.builder(options).build();
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
        private final BigInteger bulletNumId;
        private final BigInteger decimalAbstractId;
        private final XWPFNumbering numbering;

        /** Wohin Blöcke gerade geschrieben werden: der Dokumentkörper oder eine Tabellenzelle. */
        private XWPFTableCell cell;
        private boolean cellParagraphUsed;
        /** Kopf der Antworten: Anzeigename aus dem Export (die Rolle bleibt ASSISTANT). */
        private String assistantTitle = ChatExport.DEFAULT_ASSISTANT_TITLE;

        Builder(XWPFDocument document) {
            this.document = document;
            defineStyles(document);
            this.numbering = document.createNumbering();
            BigInteger bulletAbstract = numbering.addAbstractNum(new XWPFAbstractNum(listDefinition(0, true)));
            this.bulletNumId = numbering.addNum(bulletAbstract);
            this.decimalAbstractId = numbering.addAbstractNum(new XWPFAbstractNum(listDefinition(1, false)));
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
                XWPFParagraph line = listParagraph(bulletNumId, 0);
                line.createRun().setText(clean(item));
            }
        }

        private void linkSection(String label, List<String[]> items) {
            if (items.isEmpty()) {
                return;
            }
            sectionLabel(label);
            for (String[] item : items) {
                XWPFParagraph line = listParagraph(bulletNumId, 0);
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

        // -------------------------------------------------------------- Markdown

        private void markdown(String text, boolean muted) {
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            Node root = parser.parse(text.replace("\r\n", "\n"));
            blocks(root, new Context(muted));
        }

        private void blocks(Node container, Context context) {
            for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
                block(child, context);
            }
        }

        private void block(Node node, Context context) {
            if (node instanceof HtmlCommentBlock) {
                return; // wie in der Oberfläche: Kommentare bleiben unsichtbar
            }
            if (node instanceof Heading) {
                Heading heading = (Heading) node;
                XWPFParagraph paragraph = paragraph();
                if (cell == null && context.quoteDepth == 0 && context.listDepth == 0) {
                    paragraph.setStyle(STYLE_HEADING + Math.max(1, Math.min(6, heading.getLevel())));
                    inlines(paragraph, heading, new Format(), context);
                } else {
                    context.decorate(paragraph);
                    Format bold = new Format();
                    bold.bold = true;
                    inlines(paragraph, heading, bold, context);
                }
                return;
            }
            if (node instanceof Paragraph) {
                XWPFParagraph paragraph = context.pendingListParagraph != null ? context.takeListParagraph()
                        : paragraph();
                if (paragraph.getNumID() == null) {
                    context.decorate(paragraph);
                }
                inlines(paragraph, node, new Format(), context);
                return;
            }
            if (node instanceof FencedCodeBlock) {
                FencedCodeBlock fenced = (FencedCodeBlock) node;
                codeBlock(fenced.getInfo().toString().trim(), fenced.getContentChars().toString(), context);
                return;
            }
            if (node instanceof IndentedCodeBlock) {
                codeBlock("", ((IndentedCodeBlock) node).getContentChars().toString(), context);
                return;
            }
            if (node instanceof BulletList || node instanceof OrderedList) {
                list(node, context);
                return;
            }
            if (node instanceof BlockQuote) {
                context.quoteDepth++;
                try {
                    blocks(node, context);
                } finally {
                    context.quoteDepth--;
                }
                return;
            }
            if (node instanceof TableBlock) {
                table((TableBlock) node, context);
                return;
            }
            if (node instanceof ThematicBreak) {
                XWPFParagraph rule = paragraph();
                context.decorate(rule);
                rule.setBorderBottom(Borders.SINGLE);
                borderColor(rule, RULE_COLOR);
                return;
            }
            if (node instanceof HtmlBlock) {
                XWPFParagraph paragraph = paragraph();
                context.decorate(paragraph);
                text(paragraph, node.getChars().toString().trim(), new Format(), context);
                return;
            }
            if (node.hasChildren()) {
                blocks(node, context);
                return;
            }
            String rest = node.getChars().toString().trim();
            if (!rest.isEmpty()) {
                XWPFParagraph paragraph = paragraph();
                context.decorate(paragraph);
                text(paragraph, rest, new Format(), context);
            }
        }

        private void list(Node list, Context context) {
            boolean ordered = list instanceof OrderedList;
            int level = Math.min(context.listDepth, MAX_LEVELS - 1);
            BigInteger numId = bulletNumId;
            if (ordered) {
                numId = numbering.addNum(decimalAbstractId);
                CTNumLvl override = numbering.getNum(numId).getCTNum().addNewLvlOverride();
                override.setIlvl(BigInteger.valueOf(level));
                override.addNewStartOverride().setVal(BigInteger.valueOf(Math.max(0,
                        ((OrderedList) list).getStartNumber())));
            }
            context.listDepth++;
            try {
                for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                    if (!(item instanceof ListItem)) {
                        block(item, context);
                        continue;
                    }
                    context.pendingListParagraph = listParagraph(numId, level);
                    context.decorateQuote(context.pendingListParagraph);
                    blocks(item, context);
                    context.pendingListParagraph = null; // leerer Eintrag: der Absatz bleibt leer stehen
                }
            } finally {
                context.listDepth--;
            }
        }

        private void codeBlock(String info, String code, Context context) {
            String language = info.isEmpty() ? "" : info.split("\\s+")[0];
            String body = code.endsWith("\n") ? code.substring(0, code.length() - 1) : code;
            if (!language.isEmpty()) {
                XWPFParagraph caption = paragraph();
                context.decorate(caption);
                caption.setSpacingAfter(0);
                caption.setKeepNext(true);
                small(caption, language, MUTED_COLOR);
            }
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                XWPFParagraph line = paragraph();
                context.decorate(line);
                line.setSpacingBefore(0);
                line.setSpacingAfter(i == lines.length - 1 ? 160 : 0);
                line.setSpacingBetween(1.0);
                line.setBorderLeft(Borders.SINGLE);
                line.setBorderRight(Borders.SINGLE);
                if (i == 0) {
                    line.setBorderTop(Borders.SINGLE);
                }
                if (i == lines.length - 1) {
                    line.setBorderBottom(Borders.SINGLE);
                }
                if (i < lines.length - 1) {
                    line.setKeepNext(true);
                }
                borderColor(line, RULE_COLOR);
                shade(line, CODE_FILL);
                XWPFRun run = line.createRun();
                run.setFontFamily(CODE_FONT);
                run.setFontSize(CODE_POINTS);
                run.setText(clean(lines[i].replace("\t", "    ")));
            }
        }

        private void table(TableBlock block, Context context) {
            List<TableRow> rows = new ArrayList<TableRow>();
            List<Boolean> header = new ArrayList<Boolean>();
            int columns = 0;
            for (Node section = block.getFirstChild(); section != null; section = section.getNext()) {
                if (section instanceof TableSeparator) {
                    continue; // die Zeile |---|:---:| trägt nur die Ausrichtung
                }
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (row instanceof TableRow) {
                        rows.add((TableRow) row);
                        header.add(section instanceof TableHead);
                        int count = 0;
                        for (Node c = row.getFirstChild(); c != null; c = c.getNext()) {
                            if (c instanceof TableCell) {
                                count++;
                            }
                        }
                        columns = Math.max(columns, count);
                    }
                }
            }
            if (rows.isEmpty() || columns == 0) {
                return;
            }
            if (cell != null) {
                // Tabelle in einer Tabellenzelle: als Zeilen mit Trennstrichen, der Text bleibt erhalten.
                for (TableRow row : rows) {
                    XWPFParagraph paragraph = paragraph();
                    StringBuilder line = new StringBuilder();
                    for (Node c = row.getFirstChild(); c != null; c = c.getNext()) {
                        if (c instanceof TableCell) {
                            if (line.length() > 0) {
                                line.append(" | ");
                            }
                            line.append(plain(c));
                        }
                    }
                    text(paragraph, line.toString(), new Format(), context);
                }
                return;
            }
            paragraph().setSpacingAfter(0); // Abstand vor der Tabelle, sonst klebt sie am vorigen Block
            XWPFTable table = document.createTable(rows.size(), columns);
            table.setWidth("100%");
            table.setCellMargins(40, 80, 40, 80);
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow tableRow = table.getRow(r);
                boolean isHeader = header.get(r);
                if (isHeader) {
                    tableRow.setRepeatHeader(true);
                }
                int column = 0;
                for (Node c = rows.get(r).getFirstChild(); c != null; c = c.getNext()) {
                    if (!(c instanceof TableCell)) {
                        continue;
                    }
                    TableCell source = (TableCell) c;
                    XWPFTableCell target = tableRow.getCell(column++);
                    if (isHeader) {
                        target.setColor(HEADER_FILL);
                    }
                    cell = target;
                    cellParagraphUsed = false;
                    try {
                        XWPFParagraph paragraph = paragraph();
                        paragraph.setSpacingAfter(0);
                        paragraph.setAlignment(alignment(source.getAlignment()));
                        Format format = new Format();
                        format.bold = isHeader;
                        inlines(paragraph, source, format, context);
                    } finally {
                        cell = null;
                    }
                }
            }
            // Abstand nach der Tabelle, sonst klebt der nächste Absatz an ihr.
            paragraph().setSpacingAfter(0);
        }

        private ParagraphAlignment alignment(TableCell.Alignment alignment) {
            if (alignment == TableCell.Alignment.CENTER) {
                return ParagraphAlignment.CENTER;
            }
            return alignment == TableCell.Alignment.RIGHT ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT;
        }

        // -------------------------------------------------------------- Inline

        private void inlines(XWPFParagraph paragraph, Node parent, Format format, Context context) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                inline(paragraph, node, format, context);
            }
        }

        private void inline(XWPFParagraph paragraph, Node node, Format format, Context context) {
            if (node instanceof Text) {
                text(paragraph, node.getChars().unescape(), format, context);
            } else if (node instanceof StrongEmphasis) {
                Format inner = format.copy();
                inner.bold = true;
                inlines(paragraph, node, inner, context);
            } else if (node instanceof Emphasis) {
                Format inner = format.copy();
                inner.italic = true;
                inlines(paragraph, node, inner, context);
            } else if (node instanceof Strikethrough) {
                Format inner = format.copy();
                inner.strike = true;
                inlines(paragraph, node, inner, context);
            } else if (node instanceof Code) {
                Format inner = format.copy();
                inner.code = true;
                text(paragraph, ((Code) node).getText().toString(), inner, context);
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                // wie in der Oberfläche: die Zeilenstruktur der Antwort bleibt
                paragraph.createRun().addBreak();
            } else if (node instanceof Link) {
                Link link = (Link) node;
                link(paragraph, plain(link), link.getUrl().unescape(), format, context);
            } else if (node instanceof AutoLink) {
                String url = ((AutoLink) node).getUrl().unescape();
                link(paragraph, url, url, format, context);
            } else if (node instanceof MailLink) {
                String mail = ((MailLink) node).getText().toString();
                link(paragraph, mail, "mailto:" + mail, format, context);
            } else if (node instanceof Image) {
                Image image = (Image) node;
                String alt = plain(image);
                text(paragraph, "[Bild" + (alt.isEmpty() ? "" : ": " + alt) + "]", format, context);
            } else if (node instanceof RefNode) {
                reference(paragraph, (RefNode) node, format, context);
            } else if (node instanceof HtmlInlineComment) {
                return;
            } else if (node.hasChildren()) {
                inlines(paragraph, node, format, context);
            } else {
                text(paragraph, node.getChars().unescape(), format, context);
            }
        }

        /** {@code [Text][ref]}: mit Definition ein Link, ohne bleibt der Quelltext, wie er dasteht. */
        private void reference(XWPFParagraph paragraph, RefNode node, Format format, Context context) {
            Reference reference = node.isDefined() && node.getDocument() != null
                    ? node.getReferenceNode(node.getDocument()) : null;
            if (reference == null) {
                text(paragraph, node.getChars().toString(), format, context);
            } else if (node instanceof com.vladsch.flexmark.ast.ImageRef) {
                text(paragraph, "[Bild: " + plain((Node) node) + "]", format, context);
            } else {
                link(paragraph, plain((Node) node), reference.getUrl().unescape(), format, context);
            }
        }

        private void link(XWPFParagraph paragraph, String label, String url, Format format, Context context) {
            String shown = label.isEmpty() ? url : label;
            if (isWebLink(url)) {
                XWPFRun run = hyperlink(paragraph, shown, url, format.bold);
                apply(run, format, context, false);
            } else {
                text(paragraph, shown, format, context);
            }
        }

        private XWPFRun hyperlink(XWPFParagraph paragraph, String label, String url, boolean bold) {
            XWPFRun run = paragraph.createHyperlinkRun(url.trim());
            run.setText(clean(label));
            run.setColor(USER_COLOR);
            run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
            run.setBold(bold);
            return run;
        }

        private void text(XWPFParagraph paragraph, String value, Format format, Context context) {
            if (value == null || value.isEmpty()) {
                return;
            }
            String[] lines = value.split("\r?\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    paragraph.createRun().addBreak();
                }
                if (lines[i].isEmpty()) {
                    continue;
                }
                XWPFRun run = paragraph.createRun();
                run.setText(clean(lines[i]));
                apply(run, format, context, true);
            }
        }

        private void apply(XWPFRun run, Format format, Context context, boolean color) {
            if (format.bold) {
                run.setBold(true);
            }
            if (format.italic || context.quoteDepth > 0) {
                run.setItalic(true);
            }
            if (format.strike) {
                run.setStrikeThrough(true);
            }
            if (format.code) {
                run.setFontFamily(CODE_FONT);
                shade(run, CODE_FILL);
            }
            if (color && (context.muted || context.quoteDepth > 0)) {
                run.setColor(MUTED_COLOR);
            }
        }

        // -------------------------------------------------------------- Bausteine

        /** Ein neuer Absatz im Dokument oder in der aktuellen Tabellenzelle. */
        private XWPFParagraph paragraph() {
            if (cell != null) {
                if (!cellParagraphUsed && !cell.getParagraphs().isEmpty()) {
                    cellParagraphUsed = true;
                    return cell.getParagraphs().get(0);
                }
                return cell.addParagraph();
            }
            return document.createParagraph();
        }

        private XWPFParagraph listParagraph(BigInteger numId, int level) {
            XWPFParagraph paragraph = paragraph();
            paragraph.setNumID(numId);
            paragraph.setNumILvl(BigInteger.valueOf(level));
            paragraph.setSpacingAfter(40);
            return paragraph;
        }

        private void small(XWPFParagraph paragraph, String text, String color) {
            XWPFRun run = paragraph.createRun();
            run.setFontSize(SMALL_POINTS);
            run.setColor(color);
            run.setText(clean(text));
        }

        private void shade(XWPFParagraph paragraph, String fill) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = paragraph.getCTP().isSetPPr()
                    ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd = ppr.isSetShd() ? ppr.getShd()
                    : ppr.addNewShd();
            shd.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill(fill);
        }

        private void shade(XWPFRun run, String fill) {
            CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd = rpr.addNewShd();
            shd.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill(fill);
        }
    }

    /** Wo ein Block gerade steht: in Zitaten (eingerückt, grau, kursiv), in Listen, in einem Hinweis. */
    private static final class Context {
        final boolean muted;
        int quoteDepth;
        int listDepth;
        /** Der nummerierte Absatz eines Listeneintrags, den sein erster Absatz übernimmt. */
        XWPFParagraph pendingListParagraph;

        Context(boolean muted) {
            this.muted = muted;
        }

        XWPFParagraph takeListParagraph() {
            XWPFParagraph paragraph = pendingListParagraph;
            pendingListParagraph = null;
            return paragraph;
        }

        void decorate(XWPFParagraph paragraph) {
            int indent = listDepth * INDENT_STEP + quoteDepth * INDENT_STEP;
            if (indent > 0) {
                paragraph.setIndentationLeft(indent);
            }
            decorateQuote(paragraph);
        }

        void decorateQuote(XWPFParagraph paragraph) {
            if (quoteDepth > 0) {
                paragraph.setBorderLeft(Borders.SINGLE);
                borderColor(paragraph, RULE_COLOR);
                if (paragraph.getNumID() != null) {
                    paragraph.setIndentationLeft((listDepth + quoteDepth) * INDENT_STEP);
                    paragraph.setIndentationHanging(INDENT_STEP);
                }
            }
        }
    }

    private static final class Format {
        boolean bold;
        boolean italic;
        boolean strike;
        boolean code;

        Format copy() {
            Format copy = new Format();
            copy.bold = bold;
            copy.italic = italic;
            copy.strike = strike;
            copy.code = code;
            return copy;
        }
    }

    // ------------------------------------------------------------------ Formatvorlagen und Listen

    /** Standard (Calibri 11), Titel und Überschrift 1–6 als echte Word-Überschriften (Navigationsbereich). */
    private static void defineStyles(XWPFDocument document) {
        XWPFStyles styles = document.createStyles();
        CTFonts fonts = CTFonts.Factory.newInstance();
        fonts.setAscii(BODY_FONT);
        fonts.setHAnsi(BODY_FONT);
        fonts.setCs(BODY_FONT);
        fonts.setEastAsia(BODY_FONT);
        styles.setDefaultFonts(fonts);

        CTStyle normal = style("Normal", "Normal", true);
        CTRPr normalRun = normal.addNewRPr();
        normalRun.addNewSz().setVal(BigInteger.valueOf(BODY_HALF_POINTS));
        normalRun.addNewSzCs().setVal(BigInteger.valueOf(BODY_HALF_POINTS));
        CTSpacing normalSpacing = normal.addNewPPr().addNewSpacing();
        normalSpacing.setAfter(BigInteger.valueOf(120));
        normalSpacing.setLine(BigInteger.valueOf(264));
        add(styles, normal);

        CTStyle title = style(STYLE_TITLE, "Title", false);
        title.addNewBasedOn().setVal("Normal");
        title.addNewNext().setVal("Normal");
        CTPPrGeneral titleParagraph = title.addNewPPr();
        titleParagraph.addNewSpacing().setAfter(BigInteger.valueOf(60));
        CTRPr titleRun = title.addNewRPr();
        titleRun.addNewSz().setVal(BigInteger.valueOf(44));
        titleRun.addNewSzCs().setVal(BigInteger.valueOf(44));
        titleRun.addNewColor().setVal("1F2A37");
        add(styles, title);

        for (int level = 1; level <= 6; level++) {
            CTStyle heading = style(STYLE_HEADING + level, "heading " + level, false);
            heading.addNewBasedOn().setVal("Normal");
            heading.addNewNext().setVal("Normal");
            heading.addNewUiPriority().setVal(BigInteger.valueOf(9));
            heading.addNewQFormat();
            CTPPrGeneral paragraph = heading.addNewPPr();
            paragraph.addNewKeepNext();
            CTSpacing spacing = paragraph.addNewSpacing();
            spacing.setBefore(BigInteger.valueOf(level <= 2 ? 240 : 180));
            spacing.setAfter(BigInteger.valueOf(80));
            paragraph.addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
            CTRPr run = heading.addNewRPr();
            run.addNewB();
            if (level >= 5) {
                run.addNewI();
            }
            run.addNewSz().setVal(BigInteger.valueOf(HEADING_HALF_POINTS[level - 1]));
            run.addNewSzCs().setVal(BigInteger.valueOf(HEADING_HALF_POINTS[level - 1]));
            run.addNewColor().setVal("1F2A37");
            add(styles, heading);
        }
    }

    private static CTStyle style(String id, String name, boolean isDefault) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.setType(STStyleType.PARAGRAPH);
        style.addNewName().setVal(name);
        if (isDefault) {
            style.setDefault(true);
        }
        return style;
    }

    private static void add(XWPFStyles styles, CTStyle style) {
        XWPFStyle wrapped = new XWPFStyle(style, styles);
        styles.addStyle(wrapped);
    }

    private static CTAbstractNum listDefinition(int id, boolean bullet) {
        CTAbstractNum definition = CTAbstractNum.Factory.newInstance();
        definition.setAbstractNumId(BigInteger.valueOf(id));
        for (int level = 0; level < MAX_LEVELS; level++) {
            CTLvl lvl = definition.addNewLvl();
            lvl.setIlvl(BigInteger.valueOf(level));
            lvl.addNewStart().setVal(BigInteger.ONE);
            if (bullet) {
                lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
                lvl.addNewLvlText().setVal(BULLETS.get(level % BULLETS.size()));
            } else {
                lvl.addNewNumFmt().setVal(level % 3 == 0 ? STNumberFormat.DECIMAL
                        : level % 3 == 1 ? STNumberFormat.LOWER_LETTER : STNumberFormat.LOWER_ROMAN);
                lvl.addNewLvlText().setVal("%" + (level + 1) + ".");
            }
            CTInd indent = lvl.addNewPPr().addNewInd();
            indent.setLeft(BigInteger.valueOf((long) INDENT_STEP * (level + 1)));
            indent.setHanging(BigInteger.valueOf(INDENT_STEP));
        }
        return definition;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Färbt die gesetzten Absatzrahmen dezent statt schwarz. */
    private static void borderColor(XWPFParagraph paragraph, String color) {
        if (!paragraph.getCTP().isSetPPr() || !paragraph.getCTP().getPPr().isSetPBdr()) {
            return;
        }
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr borders =
                paragraph.getCTP().getPPr().getPBdr();
        for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border : Arrays.asList(
                borders.getTop(), borders.getBottom(), borders.getLeft(), borders.getRight())) {
            if (border != null) {
                border.setColor(color);
            }
        }
    }

    private static String plain(Node node) {
        StringBuilder out = new StringBuilder();
        collectText(node, out);
        return out.toString().trim();
    }

    private static void collectText(Node node, StringBuilder out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text) {
                out.append(child.getChars().unescape());
            } else if (child instanceof Code) {
                out.append(((Code) child).getText());
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                out.append(' ');
            } else if (child.hasChildren()) {
                collectText(child, out);
            } else {
                out.append(child.getChars().unescape());
            }
        }
    }

    /** Nur http, https und mailto werden echte Hyperlinks (wie DesktopLinkOpener in der Oberfläche). */
    static boolean isWebLink(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:");
    }

    /** Entfernt Zeichen, die in XML 1.0 nicht vorkommen dürfen (Steuerzeichen außer Tab). */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean bad = c < 0x20 && c != '\t' || c == 0xFFFE || c == 0xFFFF
                    || Character.isSurrogate(c) && !validSurrogate(text, i);
            if (bad) {
                if (out == null) {
                    out = new StringBuilder(text.length());
                    out.append(text, 0, i);
                }
            } else if (out != null) {
                out.append(c);
            }
        }
        return out == null ? text : out.toString();
    }

    private static boolean validSurrogate(String text, int i) {
        char c = text.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1));
        }
        return i > 0 && Character.isHighSurrogate(text.charAt(i - 1));
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
