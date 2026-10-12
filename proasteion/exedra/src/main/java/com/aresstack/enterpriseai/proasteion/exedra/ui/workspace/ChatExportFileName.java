package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Der vorgeschlagene Dateiname für „Chat exportieren …“: Titel mit {@code _} statt Leer- und Sonderzeichen, dann
 * Datum und Uhrzeit, z. B. {@code Was_ist_IAR_2026-10-12_0343}, ohne Endung. Die Endung kommt erst beim Speichern
 * aus dem gewählten Dateityp ({@link #withExtension}).
 */
final class ChatExportFileName {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ROOT);
    private static final int TITLE_LENGTH = 60;

    private ChatExportFileName() {
    }

    static String of(String title, long millis, ZoneId zone) {
        String base = title == null ? "" : title.replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        if (base.length() > TITLE_LENGTH) {
            base = base.substring(0, TITLE_LENGTH).replaceAll("_+$", "");
        }
        if (base.isEmpty()) {
            base = "Chat";
        }
        return base + "_" + STAMP.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /**
     * Die Zieldatei mit der Endung des gewählten Formats: eine andere Export-Endung ({@code .json}, {@code .md},
     * {@code .docx}) wird ersetzt, sonst wird die Endung angehängt ({@code Bericht v1.2} → {@code Bericht v1.2.docx},
     * {@code notiz.txt} → {@code notiz.txt.docx}), damit nie ein Word-Dokument mit fremder Endung entsteht.
     */
    static File withExtension(File chosen, ChatExportFormat format) {
        String name = chosen.getName().trim();
        ChatExportFormat typed = ChatExportFormat.ofFileName(name);
        if (typed != null) {
            name = name.substring(0, name.length() - typed.extension().length() - 1);
        }
        return new File(chosen.getParentFile(), name + "." + format.extension());
    }
}
