package com.aresstack.enterpriseai.app;

import com.aresstack.enterpriseai.app.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Protokolldatei der Anwendung: {@code java.util.logging} schreibt ohne Konfiguration nur auf die Konsole, die
 * es bei einem Doppelklick auf das Jar (javaw) nicht gibt. Deshalb hängt {@link #install()} beim Start einen
 * {@link FileHandler} an den Wurzel-Logger: {@code <Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log}
 * (rollierend, {@value #FILE_COUNT} Dateien je {@value #FILE_LIMIT_BYTES} Bytes, UTF-8). Die Adapter garantieren
 * Meldungen ohne Secrets; geloggt werden Konfiguration (nur Referenzen), Verbindungsfehler mit Ursachenkette und
 * Indexierungsberichte.
 *
 * <p>Scheitert das Anlegen (Verzeichnis nicht beschreibbar), startet die Anwendung trotzdem; der Grund geht an
 * die übrigen Handler. Keine statischen Felder: der Aufrufer behält den Handler und schließt ihn beim Beenden.
 */
public final class AppLogFile {

    public static final String FILE_PATTERN = "enterprise-ai-client.%g.log";
    static final int FILE_LIMIT_BYTES = 2_000_000;
    static final int FILE_COUNT = 3;
    static final int MAX_FRAMES = 30;
    static final int MAX_CAUSES = 8;

    private static final Logger LOG = Logger.getLogger(AppLogFile.class.getName());

    private AppLogFile() {
    }

    /** Das Protokollverzeichnis: {@code <Anwendungsverzeichnis>/logs}. */
    public static Path directory() {
        return AppPaths.defaultLogDirectory();
    }

    /** Hängt die Protokolldatei im Standardverzeichnis an den Wurzel-Logger; {@code null}, wenn das nicht ging. */
    public static Handler install() {
        return install(directory());
    }

    /**
     * Hängt die Protokolldatei im Verzeichnis an den Wurzel-Logger.
     *
     * @return der Handler (zum Schließen beim Beenden) oder {@code null}, wenn die Datei nicht anlegbar war
     */
    public static Handler install(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        try {
            Files.createDirectories(directory);
            FileHandler handler = new FileHandler(directory.resolve(FILE_PATTERN).toString(), FILE_LIMIT_BYTES,
                    FILE_COUNT, true);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new LineFormatter());
            handler.setLevel(Level.INFO);
            Logger.getLogger("").addHandler(handler);
            return handler;
        } catch (IOException | RuntimeException e) {
            LOG.warning("Protokolldatei unter " + directory + " nicht anlegbar: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return null;
        }
    }

    /** Entfernt und schließt den Handler; {@code null} ist erlaubt. */
    public static void uninstall(Handler handler) {
        if (handler == null) {
            return;
        }
        Logger.getLogger("").removeHandler(handler);
        handler.close();
    }

    /** Eine Zeile je Eintrag: Zeit, Stufe, Logger (Klassenname) und Meldung; Stacktrace darunter. */
    static final class LineFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder();
            line.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(record.getMillis())));
            line.append(' ').append(record.getLevel().getName());
            String logger = record.getLoggerName();
            if (logger != null && !logger.isEmpty()) {
                line.append(' ').append(logger.substring(logger.lastIndexOf('.') + 1));
            }
            line.append(": ").append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                appendTrace(line, record.getThrown());
            }
            return line.toString();
        }

        /** Stacktrace samt Ursachenkette wie {@code Throwable.printStackTrace}, nur in den Puffer geschrieben. */
        static void appendTrace(StringBuilder out, Throwable thrown) {
            Throwable current = thrown;
            int depth = 0;
            while (current != null && depth < MAX_CAUSES) {
                out.append(depth == 0 ? "" : "Caused by: ").append(current).append(System.lineSeparator());
                StackTraceElement[] frames = current.getStackTrace();
                int shown = Math.min(frames.length, MAX_FRAMES);
                for (int i = 0; i < shown; i++) {
                    out.append("\tat ").append(frames[i]).append(System.lineSeparator());
                }
                if (frames.length > shown) {
                    out.append("\t... ").append(frames.length - shown).append(" more").append(System.lineSeparator());
                }
                Throwable next = current.getCause();
                current = next == current ? null : next;
                depth++;
            }
        }
    }
}
