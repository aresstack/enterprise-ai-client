package com.aresstack.enterpriseai.proasteion.exedra.document;

/** The formats of the page export (#115), each with its menu label, file extension and writer. */
public enum ExportFormat {
    PDF("Als PDF …", "PDF-Dokument", "pdf"),
    DOCX("Als Word …", "Word-Dokument", "docx"),
    MARKDOWN("Als Markdown …", "Markdown", "md");

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

    /** Return a new writer of this format. */
    public ExportWriter writer() {
        switch (this) {
            case PDF:
                return new PdfExportWriter();
            case DOCX:
                return new DocxExportWriter();
            default:
                return new MarkdownExportWriter();
        }
    }
}
