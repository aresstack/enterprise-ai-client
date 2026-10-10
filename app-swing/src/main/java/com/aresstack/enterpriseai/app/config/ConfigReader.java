package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Typisiertes Lesen einer {@link Properties}-Konfiguration. Sammelt Probleme statt beim ersten abzubrechen, damit
 * der Benutzer alle Fehler auf einmal sieht; jede Meldung nennt nur Schlüssel und Erwartung, nie den Wert.
 */
final class ConfigReader {

    private final Properties properties;
    private final List<String> problems = new ArrayList<String>();
    private final Set<String> readKeys = new LinkedHashSet<String>();

    ConfigReader(Properties properties) {
        this.properties = properties;
    }

    List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    boolean hasProblems() {
        return !problems.isEmpty();
    }

    void problem(String key, String expectation) {
        problems.add(key + ": " + expectation);
    }

    /** Anzahl der bisher vermerkten Probleme, als Marke für {@link #takeProblemsSince}. */
    int problemCount() {
        return problems.size();
    }

    /** Nimmt die seit {@code mark} vermerkten Probleme heraus (sie zählen danach nicht mehr) und liefert sie. */
    List<String> takeProblemsSince(int mark) {
        List<String> taken = new ArrayList<String>(problems.subList(mark, problems.size()));
        problems.subList(mark, problems.size()).clear();
        return taken;
    }

    /** Vermerkt alle Schlüssel mit diesem Präfix als gelesen (z. B. die einer übersprungenen Quelle). */
    void markRead(String prefix) {
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                readKeys.add(key);
            }
        }
    }

    /** Schlüssel der Datei, die nie gelesen wurden (Tippfehler), in Dateireihenfolge nicht garantiert. */
    List<String> unreadKeys() {
        List<String> unread = new ArrayList<String>();
        for (String key : properties.stringPropertyNames()) {
            if (!readKeys.contains(key)) {
                unread.add(key);
            }
        }
        Collections.sort(unread);
        return unread;
    }

    boolean has(String key) {
        readKeys.add(key);
        String value = properties.getProperty(key);
        return value != null && !value.trim().isEmpty();
    }

    /** Getrimmter Text oder {@code def}, wenn der Schlüssel fehlt oder leer ist. */
    String text(String key, String def) {
        readKeys.add(key);
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        return value.trim();
    }

    /** Pflichttext; fehlt er, wird ein Problem vermerkt und {@code null} geliefert. */
    String required(String key) {
        String value = text(key, null);
        if (value == null) {
            problem(key, "fehlt (Pflichtangabe)");
        }
        return value;
    }

    int integer(String key, int def, int min, int max) {
        String value = text(key, null);
        if (value == null) {
            return def;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                problem(key, "muss zwischen " + min + " und " + max + " liegen");
                return def;
            }
            return parsed;
        } catch (NumberFormatException e) {
            problem(key, "keine ganze Zahl");
            return def;
        }
    }

    double decimal(String key, double def, double min, double max) {
        String value = text(key, null);
        if (value == null) {
            return def;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || parsed < min || parsed > max) {
                problem(key, "muss zwischen " + min + " und " + max + " liegen");
                return def;
            }
            return parsed;
        } catch (NumberFormatException e) {
            problem(key, "keine Zahl");
            return def;
        }
    }

    boolean bool(String key, boolean def) {
        String value = text(key, null);
        if (value == null) {
            return def;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "yes".equals(lower) || "ja".equals(lower) || "on".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "nein".equals(lower) || "off".equals(lower) || "0".equals(lower)) {
            return false;
        }
        problem(key, "muss true oder false sein");
        return def;
    }

    /** Absolute http(s)-URI ohne Benutzerinfo, Query und Fragment; {@code null} bei Fehler oder fehlendem Wert. */
    URI httpUri(String key, boolean required) {
        String value = required ? required(key) : text(key, null);
        if (value == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            problem(key, "keine gültige URL");
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            problem(key, "muss eine absolute http(s)-URL mit Host sein");
            return null;
        }
        if (uri.getRawUserInfo() != null) {
            problem(key, "darf keine Zugangsdaten enthalten (SecretRef verwenden)");
            return null;
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            problem(key, "darf weder Query noch Fragment enthalten");
            return null;
        }
        return uri;
    }

    <E extends Enum<E>> E enumValue(String key, Class<E> type, E def) {
        String value = text(key, null);
        if (value == null) {
            return def;
        }
        String upper = value.toUpperCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(upper)) {
                return constant;
            }
        }
        StringBuilder allowed = new StringBuilder();
        for (E constant : type.getEnumConstants()) {
            if (allowed.length() > 0) {
                allowed.append(", ");
            }
            allowed.append(constant.name());
        }
        problem(key, "muss einer der Werte " + allowed + " sein");
        return def;
    }

    /** Kommagetrennte Liste; leere Einträge werden übersprungen. */
    List<String> list(String key) {
        String value = text(key, null);
        List<String> items = new ArrayList<String>();
        if (value == null) {
            return items;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    Path path(String key, Path def) {
        String value = text(key, null);
        if (value == null) {
            return def;
        }
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            problem(key, "kein gültiger Pfad");
            return def;
        }
    }

    /** Optionaler {@link SecretRef}; die Referenz ist kein Secret, nur der Verweis auf einen KeePass-Eintrag. */
    SecretRef secretRef(String key) {
        String value = text(key, null);
        if (value == null) {
            return null;
        }
        try {
            return SecretRef.of(value);
        } catch (IllegalArgumentException e) {
            problem(key, "keine gültige Secret-Referenz");
            return null;
        }
    }

    /** Optionale Zahl; {@code null}, wenn nicht gesetzt oder ungültig (dann ist ein Problem vermerkt). */
    Integer optionalInteger(String key, int min, int max) {
        if (!has(key)) {
            return null;
        }
        int before = problems.size();
        int value = integer(key, 0, min, max);
        return problems.size() == before ? Integer.valueOf(value) : null;
    }

    /** Optionale Dezimalzahl; {@code null}, wenn nicht gesetzt oder ungültig (dann ist ein Problem vermerkt). */
    Double optionalDecimal(String key, double min, double max) {
        if (!has(key)) {
            return null;
        }
        int before = problems.size();
        double value = decimal(key, 0.0, min, max);
        return problems.size() == before ? Double.valueOf(value) : null;
    }
}
