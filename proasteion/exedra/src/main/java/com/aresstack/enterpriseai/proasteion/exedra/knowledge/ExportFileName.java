package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.document.ExportFormat;

import java.io.File;
import java.util.Locale;

/**
 * The file name a page export (#115) suggests: the title of the page without characters file systems reject, e.g.
 * {@code Grobkonzept Labor und Fabrik}; the extension comes from the format ({@link #withExtension}).
 */
final class ExportFileName {

    private static final int MAX_LENGTH = 120;
    private static final String FALLBACK = "Export";

    private ExportFileName() {
    }

    static String of(String title) {
        String name = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ").trim().replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH).trim();
        }
        return name.isEmpty() ? FALLBACK : name;
    }

    /** The chosen file with the extension of {@code format}, appended unless it is already there. */
    static File withExtension(File chosen, ExportFormat format) {
        String name = chosen.getName().trim();
        String suffix = "." + format.extension();
        if (!name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            name = name + suffix;
        }
        return new File(chosen.getParentFile(), name);
    }
}
