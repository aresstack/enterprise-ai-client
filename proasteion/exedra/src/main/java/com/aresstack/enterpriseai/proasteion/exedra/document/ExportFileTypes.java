package com.aresstack.enterpriseai.proasteion.exedra.document;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The file types of one export dialog (#132), shared by the chat, the reader and the page tree: every export opens the
 * same save dialog with its formats as file type filters, the first one (PDF) preselected, and a suggested name without
 * extension. The chosen filter alone decides the writer and the extension ({@link #target}). Contexts may offer
 * different formats (JSON only for chats). Immutable.
 *
 * @param <T> the format of the context, e.g. {@link ExportFormat}
 */
public final class ExportFileTypes<T> {

    /** One file type of the dialog: its label, its extension without dot and the format it stands for. */
    public static final class Type<T> {
        private final String label;
        private final String extension;
        private final T format;

        public Type(String label, String extension, T format) {
            if (label == null || extension == null || extension.isEmpty() || format == null) {
                throw new IllegalArgumentException("label, extension and format are required");
            }
            this.label = label;
            this.extension = extension.toLowerCase(Locale.ROOT);
            this.format = format;
        }

        public String label() {
            return label;
        }

        public String extension() {
            return extension;
        }

        public T format() {
            return format;
        }
    }

    private final List<Type<T>> types;

    /** @param types the offered types; the first is preselected */
    public ExportFileTypes(List<Type<T>> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("at least one type is required");
        }
        this.types = Collections.unmodifiableList(new ArrayList<Type<T>>(types));
    }

    /** The page export's types: PDF, Word, Markdown. */
    public static ExportFileTypes<ExportFormat> pages() {
        List<Type<ExportFormat>> types = new ArrayList<Type<ExportFormat>>();
        for (ExportFormat format : ExportFormat.values()) {
            types.add(new Type<ExportFormat>(format.fileLabel(), format.extension(), format));
        }
        return new ExportFileTypes<ExportFormat>(types);
    }

    public List<Type<T>> types() {
        return types;
    }

    /** The type selected when the dialog opens. */
    public Type<T> preselected() {
        return types.get(0);
    }

    /** The type of {@code format}, or {@code null}. */
    public Type<T> of(T format) {
        for (Type<T> type : types) {
            if (type.format.equals(format)) {
                return type;
            }
        }
        return null;
    }

    /**
     * The file to write: the chosen name with the extension of {@code type}. An extension of any offered type is
     * replaced, whatever its case ({@code Bericht.PDF} as Word → {@code Bericht.docx}), so no {@code a.pdf.docx}
     * appears; any other ending stays part of the name ({@code Bericht v1.2} → {@code Bericht v1.2.pdf}).
     */
    public File target(File chosen, Type<T> type) {
        String name = chosen.getName().trim();
        String lower = name.toLowerCase(Locale.ROOT);
        for (Type<T> known : types) {
            String suffix = "." + known.extension;
            if (lower.endsWith(suffix) && name.length() > suffix.length()) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        return new File(chosen.getParentFile(), name + "." + type.extension);
    }

    /** The file name without an extension of the offered types, as the dialog suggests it. */
    public String withoutExtension(String name) {
        String trimmed = name == null ? "" : name.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (Type<T> known : types) {
            String suffix = "." + known.extension;
            if (lower.endsWith(suffix) && trimmed.length() > suffix.length()) {
                return trimmed.substring(0, trimmed.length() - suffix.length());
            }
        }
        return trimmed;
    }
}
