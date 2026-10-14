package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chapter of a page export (#115): the title of the page, its current content and its child pages in the order of
 * the source. The tree is complete when it leaves {@link KnowledgeExportUseCase}; every output format writes from it
 * and none loads anything itself. Immutable.
 */
public final class ExportDocumentNode {

    private final String title;
    private final KnowledgeDocument document;
    private final List<ExportDocumentNode> children;

    /**
     * @param title    the chapter heading; blank falls back to the title of the document
     * @param document the content of the page; {@code null} for a node without own content (a space root)
     * @param children the child chapters in source order
     */
    public ExportDocumentNode(String title, KnowledgeDocument document, List<ExportDocumentNode> children) {
        String heading = title == null ? "" : title.trim();
        if (heading.isEmpty() && document != null) {
            heading = document.resource().title().trim();
        }
        this.title = heading.isEmpty() ? (document == null ? "Ohne Titel" : document.id().value()) : heading;
        this.document = document;
        this.children = children == null ? Collections.<ExportDocumentNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExportDocumentNode>(children));
    }

    public String title() {
        return title;
    }

    /** Return the page content; {@code null} for a node without own content. */
    public KnowledgeDocument document() {
        return document;
    }

    /** Return the text of the page in canonical Markdown; empty without content. */
    public String text() {
        return document == null ? "" : document.text();
    }

    public List<ExportDocumentNode> children() {
        return children;
    }

    /** Return the number of chapters of this subtree, this one included. */
    public int size() {
        int size = 1;
        for (ExportDocumentNode child : children) {
            size += child.size();
        }
        return size;
    }
}
