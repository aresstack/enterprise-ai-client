package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.document.ExportFileTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Dateiarten von „Chat exportieren …“: PDF (im Dialog voreingestellt, #132), Word und Markdown als lesbare
 * Gesprächsberichte, JSON als maßgebliche, verlustarme Exportstruktur.
 */
public enum ChatExportFormat {

    PDF("PDF-Dokument (*.pdf)", "pdf"),
    DOCX("Word-Dokument (*.docx)", "docx"),
    MARKDOWN("Markdown (*.md)", "md"),
    JSON("JSON (*.json)", "json");

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

    /** Die Dateitypen des gemeinsamen Speichern-Dialogs, PDF zuerst. */
    public static ExportFileTypes<ChatExportFormat> fileTypes() {
        List<ExportFileTypes.Type<ChatExportFormat>> types = new ArrayList<ExportFileTypes.Type<ChatExportFormat>>();
        for (ChatExportFormat format : values()) {
            types.add(new ExportFileTypes.Type<ChatExportFormat>(format.label, format.extension, format));
        }
        return new ExportFileTypes<ChatExportFormat>(types);
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
