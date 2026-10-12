package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Der vorgeschlagene Dateiname für „Chat exportieren …“: Titel mit {@code _} statt Leer- und Sonderzeichen, dann
 * Datum und Uhrzeit, z. B. {@code Was_ist_IAR_2026-10-12_0343.json}.
 */
final class ChatExportFileName {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ROOT);
    private static final int TITLE_LENGTH = 60;

    private ChatExportFileName() {
    }

    static String of(String title, long millis, ZoneId zone, String extension) {
        String base = title == null ? "" : title.replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        if (base.length() > TITLE_LENGTH) {
            base = base.substring(0, TITLE_LENGTH).replaceAll("_+$", "");
        }
        if (base.isEmpty()) {
            base = "Chat";
        }
        return base + "_" + STAMP.format(Instant.ofEpochMilli(millis).atZone(zone)) + "." + extension;
    }
}
