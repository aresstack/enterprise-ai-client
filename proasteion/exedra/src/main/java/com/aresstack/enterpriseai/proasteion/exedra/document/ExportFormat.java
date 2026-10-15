package com.aresstack.enterpriseai.proasteion.exedra.document;

/**
 * The formats of the page export (#115), each with its file type label, file extension and writer. Since #132 the
 * format is chosen in the save dialog ({@link ExportFileTypes}); PDF comes first and is preselected.
 */
public enum ExportFormat {
    PDF("Als PDF …", "PDF-Dokument (*.pdf)", "pdf"),
    DOCX("Als Word …", "Word-Dokument (*.docx)", "docx"),
    MARKDOWN("Als Markdown …", "Markdown (*.md)", "md");

    private final String menuLabel;
    private final String fileLabel;
    private final String extension;

    ExportFormat(String menuLabel, String fileLabel, String extension) {
        this.menuLabel = menuLabel;
        this.fileLabel = fileLabel;
        this.extension = extension;
    }

    /** Return the entry of the export menu, e.g. {@code "Als PDF …"}. */
    public String menuLabel() {
        return menuLabel;
    }

    /** Return the name of the file type in the save dialog. */
    public String fileLabel() {
        return fileLabel;
    }

    public String extension() {
        return extension;
    }

    /** Return a new writer of this format with the figures of the application. */
    public ExportWriter writer() {
        return writer(ExportGraphics.standard());
    }

    /** Return a new writer of this format that embeds figures through {@code graphics}. */
    public ExportWriter writer(ExportGraphics graphics) {
        switch (this) {
            case PDF:
                return new PdfExportWriter(graphics);
            case DOCX:
                return new DocxExportWriter(graphics);
            default:
                return new MarkdownExportWriter(graphics);
        }
    }
}
