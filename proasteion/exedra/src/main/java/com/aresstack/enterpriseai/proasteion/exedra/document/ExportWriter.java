package com.aresstack.enterpriseai.proasteion.exedra.document;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;

import java.io.IOException;
import java.io.OutputStream;

/**
 * One output format of the page export (#115): writes a complete {@link ExportDocumentNode} tree as one file. A writer
 * only lays out what the tree holds; it never loads pages itself, so a single page and a whole subtree go through the
 * same writer.
 */
public interface ExportWriter {

    void write(ExportDocumentNode root, OutputStream out) throws IOException;
}
