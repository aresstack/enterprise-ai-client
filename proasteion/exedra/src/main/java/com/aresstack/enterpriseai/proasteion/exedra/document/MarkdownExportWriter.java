package com.aresstack.enterpriseai.proasteion.exedra.document;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Writes the export tree as one Markdown file (#115): every page a heading as deep as its place in the tree (root
 * {@code #}, child {@code ##} …), its content below with every own heading shifted under that one. A heading deeper
 * than Markdown's six levels becomes a bold line instead of an invalid heading.
 */
public final class MarkdownExportWriter implements ExportWriter {

    /** The deepest heading Markdown knows. */
    static final int MAX_LEVEL = 6;

    private final Parser parser;

    public MarkdownExportWriter() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Collections.singletonList(TablesExtension.create()));
        this.parser = Parser.builder(options).build();
    }

    @Override
    public void write(ExportDocumentNode root, OutputStream out) throws IOException {
        out.write(render(root).getBytes(StandardCharsets.UTF_8));
    }

    /** Return the whole tree as Markdown. */
    public String render(ExportDocumentNode root) {
        StringBuilder text = new StringBuilder();
        chapter(root, 1, text);
        return text.toString().replaceAll("\\n{3,}", "\n\n").trim() + "\n";
    }

    private void chapter(ExportDocumentNode node, int depth, StringBuilder out) {
        out.append(heading(depth, node.title())).append("\n\n");
        String content = node.text().replace("\r\n", "\n").trim();
        if (!content.isEmpty()) {
            out.append(shiftHeadings(content, depth)).append("\n\n");
        }
        for (ExportDocumentNode child : node.children()) {
            chapter(child, depth + 1, out);
        }
    }

    static String heading(int level, String text) {
        String line = text.replace('\n', ' ').trim();
        if (level > MAX_LEVEL) {
            return "**" + line + "**";
        }
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < level; i++) {
            marks.append('#');
        }
        return marks + " " + line;
    }

    /** Move every heading of {@code markdown} {@code shift} levels deeper; code blocks and other text stay as they are. */
    String shiftHeadings(String markdown, int shift) {
        if (shift <= 0) {
            return markdown;
        }
        Document document = parser.parse(markdown);
        List<Heading> headings = new ArrayList<Heading>();
        collect(document, headings);
        StringBuilder out = new StringBuilder(markdown);
        for (int i = headings.size() - 1; i >= 0; i--) {
            Heading heading = headings.get(i);
            if (heading.isSetextHeading() && !(heading.getParent() instanceof Document)) {
                continue; // a two-line heading inside a quote or list keeps its markers
            }
            int start = heading.getStartOffset();
            int end = heading.getEndOffset();
            while (end > start && (markdown.charAt(end - 1) == '\n' || markdown.charAt(end - 1) == '\r')) {
                end--;
            }
            out.replace(start, end, heading(heading.getLevel() + shift, heading.getText().toString()));
        }
        return out.toString();
    }

    private static void collect(Node node, List<Heading> headings) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading) {
                headings.add((Heading) child);
            } else {
                collect(child, headings);
            }
        }
    }
}
