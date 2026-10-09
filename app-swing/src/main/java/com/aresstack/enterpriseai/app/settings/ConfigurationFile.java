package com.aresstack.enterpriseai.app.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Die Konfigurationsdatei (Properties, UTF-8) als editierbarer Text: {@link #update} setzt und entfernt
 * einzelne Schlüssel und lässt alles andere stehen, Kommentare, Reihenfolge und unbekannte Schlüssel
 * eingeschlossen. Ein gesetzter Schlüssel ersetzt seine aktive Zeile, aktiviert sonst eine auskommentierte
 * Zeile {@code #schlüssel=...} (so bleibt die kommentierte Vorlage lesbar) und wird sonst am Ende ergänzt.
 * Ein entfernter Schlüssel wird auskommentiert, nicht gelöscht. Geschrieben wird atomar über eine
 * Temporärdatei im selben Verzeichnis.
 *
 * <p>Werte werden nach den Regeln von {@link Properties} maskiert (Backslash, Zeilenumbruch, führendes
 * Leerzeichen), damit {@code Properties.load} sie unverändert zurückliest; Nicht-ASCII bleibt lesbar, weil die
 * Datei als UTF-8 gelesen wird.
 */
public final class ConfigurationFile {

    /** Überschrift vor Schlüsseln, die am Ende ergänzt wurden, weil die Datei sie noch nicht kannte. */
    public static final String APPENDED_HEADER = "# --- Vom Einstellungen-Dialog ergänzt ------------------------------------------------------------";

    private final Path path;

    public ConfigurationFile(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    /** Die Schlüssel der Datei; leer, wenn sie fehlt. */
    public Properties read() throws IOException {
        Properties properties = new Properties();
        if (!exists()) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    /** Legt die Datei mit diesem Inhalt an, falls sie fehlt. @return {@code true}, wenn sie angelegt wurde */
    public boolean createIfMissing(String content) throws IOException {
        if (exists()) {
            return false;
        }
        writeAtomically(content == null ? "" : content);
        return true;
    }

    /**
     * Entfernt (kommentiert aus) die Schlüssel aus {@code remove} und setzt danach die aus {@code set}.
     * Fehlt die Datei, ist {@code template} der Ausgangstext ({@code null}: leer).
     */
    public void update(Map<String, String> set, Collection<String> remove, String template) throws IOException {
        String original = exists() ? readText() : (template == null ? "" : template);
        String updated = apply(original, set == null ? Collections.<String, String>emptyMap() : set,
                remove == null ? Collections.<String>emptySet() : remove);
        writeAtomically(updated);
    }

    // ------------------------------------------------------------------ Textbearbeitung

    /** Die Bearbeitung auf einem Text; für Tests ohne Datei zugänglich. */
    static String apply(String text, Map<String, String> set, Collection<String> remove) {
        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        List<LogicalLine> lines = split(text);
        Map<String, Integer> active = new HashMap<String, Integer>();
        Map<String, Integer> commented = new HashMap<String, Integer>();
        for (int i = 0; i < lines.size(); i++) {
            LogicalLine line = lines.get(i);
            if (line.key == null) {
                continue;
            }
            if (line.commentedOut) {
                if (!commented.containsKey(line.key)) {
                    commented.put(line.key, i);
                }
            } else {
                active.put(line.key, i); // die letzte aktive Zeile gewinnt, wie bei Properties.load
            }
        }
        Set<String> removed = new LinkedHashSet<String>(remove);
        removed.removeAll(set.keySet());
        for (String key : removed) {
            for (LogicalLine line : lines) {
                if (!line.commentedOut && key.equals(line.key)) {
                    line.commentOut();
                }
            }
            active.remove(key);
        }
        List<String> appended = new ArrayList<String>();
        for (Map.Entry<String, String> entry : set.entrySet()) {
            String key = entry.getKey();
            String rendered = escapeKey(key) + "=" + escapeValue(entry.getValue());
            if (active.containsKey(key)) {
                int index = active.get(key);
                for (int i = 0; i < lines.size(); i++) {
                    LogicalLine line = lines.get(i);
                    if (i != index && !line.commentedOut && key.equals(line.key)) {
                        line.commentOut(); // frühere Dubletten eindeutig machen
                    }
                }
                lines.get(index).replace(rendered);
            } else if (commented.containsKey(key)) {
                lines.get(commented.get(key)).replace(rendered);
            } else {
                appended.add(rendered);
            }
        }
        StringBuilder out = new StringBuilder();
        for (LogicalLine line : lines) {
            for (String physical : line.physical) {
                out.append(physical).append(eol);
            }
        }
        if (!appended.isEmpty()) {
            boolean hasHeader = false;
            for (LogicalLine line : lines) {
                if (line.physical.size() == 1 && APPENDED_HEADER.equals(line.physical.get(0).trim())) {
                    hasHeader = true;
                }
            }
            if (!hasHeader) {
                if (out.length() > 0 && !endsWithBlankLine(out, eol)) {
                    out.append(eol);
                }
                out.append(APPENDED_HEADER).append(eol);
            }
            for (String line : appended) {
                out.append(line).append(eol);
            }
        }
        return out.toString();
    }

    private static boolean endsWithBlankLine(StringBuilder out, String eol) {
        String tail = eol + eol;
        return out.length() >= tail.length() && out.substring(out.length() - tail.length()).equals(tail);
    }

    /** Teilt in logische Zeilen (Fortsetzungen mit Backslash am Ende gehören zur selben Zeile). */
    private static List<LogicalLine> split(String text) {
        List<LogicalLine> lines = new ArrayList<LogicalLine>();
        String[] physical = text.split("\r\n|\r|\n", -1);
        int end = physical.length;
        if (end > 0 && physical[end - 1].isEmpty()) {
            end--; // abschließender Zeilenumbruch erzeugt kein leeres Element
        }
        int i = 0;
        while (i < end) {
            List<String> group = new ArrayList<String>();
            group.add(physical[i]);
            boolean comment = isComment(physical[i]);
            while (!comment && continues(physical[i]) && i + 1 < end) {
                i++;
                group.add(physical[i]);
            }
            i++;
            lines.add(new LogicalLine(group));
        }
        return lines;
    }

    private static boolean isComment(String line) {
        String trimmed = stripLeading(line);
        return trimmed.startsWith("#") || trimmed.startsWith("!");
    }

    /** Ungerade Zahl Backslashes am Ende: die nächste physische Zeile setzt den Wert fort. */
    private static boolean continues(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static String stripLeading(String line) {
        int i = 0;
        while (i < line.length() && isPropertiesWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(i);
    }

    private static boolean isPropertiesWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    /**
     * Der Schlüssel einer Properties-Zeile (ohne führenden Kommentar), entmaskiert; {@code null} bei einer
     * Leerzeile. {@code requireSeparator} verlangt {@code =} oder {@code :} nach dem Schlüssel, damit ein
     * erklärender Kommentar wie {@code # chat.model und chat.baseUrl sind Pflicht} nicht als Schlüssel zählt.
     */
    static String parseKey(String line, boolean requireSeparator) {
        String s = stripLeading(line);
        if (s.isEmpty()) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '=' || c == ':' || isPropertiesWhitespace(c)) {
                break;
            }
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'u' && i + 5 < s.length()) {
                    try {
                        key.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 6;
                        continue;
                    } catch (NumberFormatException e) {
                        // kein Unicode-Escape: Zeichen übernehmen
                    }
                }
                key.append(next == 't' ? '\t' : next == 'n' ? '\n' : next == 'r' ? '\r' : next == 'f' ? '\f' : next);
                i += 2;
                continue;
            }
            key.append(c);
            i++;
        }
        if (requireSeparator) {
            while (i < s.length() && isPropertiesWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= s.length() || (s.charAt(i) != '=' && s.charAt(i) != ':')) {
                return null;
            }
        }
        return key.length() == 0 ? null : key.toString();
    }

    static String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case ' ':
                    out.append(i == 0 ? "\\ " : " ");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    static String escapeKey(String key) {
        StringBuilder out = new StringBuilder(key.length() + 8);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            switch (c) {
                case '\\':
                case ' ':
                case '=':
                case ':':
                case '#':
                case '!':
                    out.append('\\').append(c);
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ Datei

    private String readText() throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void writeAtomically(String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Fester Präfix: createTempFile verlangt mindestens drei Zeichen, der Dateiname darf kürzer sein.
        Path temp = Files.createTempFile(parent, "enterprise-ai-config-", ".tmp");
        try {
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Eine logische Zeile: Schlüssel (falls vorhanden), auskommentiert oder aktiv, physische Zeilen. */
    private static final class LogicalLine {
        final List<String> physical;
        String key;
        boolean commentedOut;

        LogicalLine(List<String> physical) {
            this.physical = physical;
            String first = physical.get(0);
            String trimmed = stripLeading(first);
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                String rest = trimmed.substring(1);
                if (rest.startsWith(" ")) {
                    rest = rest.substring(1);
                }
                // Nur "#schlüssel=wert" bzw. "# schlüssel=wert" gilt als deaktivierter Schlüssel.
                if (!rest.isEmpty() && !isPropertiesWhitespace(rest.charAt(0))) {
                    this.key = parseKey(rest, true);
                }
                this.commentedOut = true;
            } else {
                StringBuilder joined = new StringBuilder(first);
                for (int i = 1; i < physical.size(); i++) {
                    joined.append(stripLeading(physical.get(i)));
                }
                this.key = parseKey(joined.toString(), false);
                this.commentedOut = false;
            }
        }

        void replace(String line) {
            physical.clear();
            physical.add(line);
            commentedOut = false;
        }

        void commentOut() {
            for (int i = 0; i < physical.size(); i++) {
                physical.set(i, "#" + physical.get(i));
            }
            commentedOut = true;
        }
    }
}
