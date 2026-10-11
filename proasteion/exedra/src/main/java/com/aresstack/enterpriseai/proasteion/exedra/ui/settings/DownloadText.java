package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.Locale;

/** Texte der Download-Anzeige: „Datei n von m: name – p % von x MB“. */
final class DownloadText {

    private DownloadText() {
    }

    /** Prozent der aktuellen Datei, 0–100; {@code -1}, wenn die Größe unbekannt ist. */
    static int percent(long done, long total) {
        if (total <= 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, done * 100 / total));
    }

    static String line(String name, int number, int count, long done, long total) {
        String amount = total > 0 ? percent(done, total) + " % von " + megabytes(total) : megabytes(done);
        return "Datei " + number + " von " + count + ": " + name + " – " + amount;
    }

    static String megabytes(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
