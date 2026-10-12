package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.Locale;

/** Texte der Download-Anzeige: „Datei n von m: name – p % von x MB“. */
public final class DownloadText {

    private DownloadText() {
    }

    /** Prozent der aktuellen Datei, 0–100; {@code -1}, wenn die Größe unbekannt ist. */
    public static int percent(long done, long total) {
        if (total <= 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, done * 100 / total));
    }

    public static String line(String name, int number, int count, long done, long total) {
        String amount = total > 0 ? percent(done, total) + " % von " + megabytes(total) : megabytes(done);
        return "Datei " + number + " von " + count + ": " + name + " – " + amount;
    }

    /** Text inside the bar: „44 % · 739,6 MB / 1,68 GB“, without a known size only the loaded amount. */
    public static String amount(long done, long total) {
        if (total <= 0) {
            return size(done);
        }
        return percent(done, total) + " % · " + size(done) + " / " + size(total);
    }

    /** Secondary line below the bar: „Datei 10 von 12 · name“. */
    public static String file(String name, int number, int count) {
        return "Datei " + number + " von " + count + " · " + name;
    }

    /** Megabytes below one gigabyte, gigabytes above. */
    public static String size(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        if (megabytes < 1024.0) {
            return String.format(Locale.GERMANY, "%.1f MB", megabytes);
        }
        return String.format(Locale.GERMANY, "%.2f GB", megabytes / 1024.0);
    }

    public static String megabytes(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
