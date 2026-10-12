package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import java.util.Locale;

/** Die Dateiarten von „Chat exportieren …“: JSON (maßgeblich), Markdown und Word als lesbare Sichten. */
public enum ChatExportFormat {

    JSON("JSON (*.json)", "json"),
    MARKDOWN("Markdown (*.md)", "md"),
    DOCX("Word-Dokument (*.docx)", "docx");

    private final String label;
    private final String extension;

    ChatExportFormat(String label, String extension) {
        this.label = label;
        this.extension = extension;
    }

    /** Die Beschriftung im Speichern-Dialog. */
    public String label() {
        return label;
    }

    /** Die Dateiendung ohne Punkt. */
    public String extension() {
        return extension;
    }

    /** Das Format, dessen Endung der Dateiname trägt, oder {@code null}. */
    public static ChatExportFormat ofFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (ChatExportFormat format : values()) {
            if (lower.endsWith("." + format.extension)) {
                return format;
            }
        }
        return null;
    }
}
