package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Beschafft die Konfiguration beim Start. Fehlt die Datei, öffnet sich mit Oberfläche der Einstellungen-Dialog
 * (leere Pflichtfelder, KeePass-Titel vorgeschlagen) statt dass sich die Anwendung beendet; die Datei entsteht
 * erst beim Speichern aus der kommentierten Vorlage. Bricht der Benutzer ab, bleibt keine Datei zurück, damit
 * der nächste Start den Dialog wieder zeigt und nicht die Beispielwerte der Vorlage lädt (die Vorlage ist
 * absichtlich ladbar). Ohne Oberfläche (headless) bleibt es beim bisherigen Verhalten: Vorlage anlegen, Hinweis,
 * Exit-Code 2. Eine vorhandene, aber fehlerhafte Datei öffnet den Dialog mit ihren Werten und den Problemen;
 * headless wird wie bisher gemeldet. Der Dialog speichert selbst (über {@code SettingsDialogActions}); hier
 * wird nach jeder Runde neu geladen, bis die Datei lädt oder der Benutzer abbricht.
 */
public final class ConfigurationStartup {

    /** Die Oberfläche, die den Entwurf bearbeiten lässt; {@code null} als Rückgabe heißt Abbruch. */
    public interface SettingsUi {
        SettingsForm edit(SettingsForm initial, List<String> problems, boolean firstStart);
    }

    /** Ergebnis: geladene Konfiguration, oder ein Grund, warum die Anwendung nicht startet. */
    public static final class Outcome {
        private final AppConfig config;
        private final String message;
        private final boolean cancelled;

        private Outcome(AppConfig config, String message, boolean cancelled) {
            this.config = config;
            this.message = message;
            this.cancelled = cancelled;
        }

        static Outcome started(AppConfig config) {
            return new Outcome(config, null, false);
        }

        static Outcome failed(String message) {
            return new Outcome(null, message, false);
        }

        static Outcome cancelled(String message) {
            return new Outcome(null, message, true);
        }

        public boolean isStarted() {
            return config != null;
        }

        /** Der Benutzer hat den Dialog geschlossen, ohne zu speichern: kein Fehler, nur kein Start. */
        public boolean isCancelled() {
            return cancelled;
        }

        public AppConfig config() {
            return config;
        }

        /** Meldung für Log und Dialog, ohne Werte aus der Datei; {@code null} bei Erfolg. */
        public String message() {
            return message;
        }
    }

    private ConfigurationStartup() {
    }

    /**
     * @param ui der Dialog, oder {@code null} ohne Display (headless): dann Vorlage und Meldung wie bisher
     */
    public static Outcome obtain(ConfigurationFile file, SettingsUi ui) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (!file.exists()) {
            if (ui == null) {
                try {
                    file.createIfMissing(AppConfigLoader.exampleConfiguration());
                } catch (IOException e) {
                    return Outcome.failed("Es gibt keine Konfiguration unter\n" + file.path() + "\nund die Vorlage "
                            + "konnte dort nicht angelegt werden (" + e.getClass().getSimpleName() + ").");
                }
                return Outcome.failed(templateCreated(file));
            }
            return editUntilLoadable(file, ui, SettingsMapper.firstStartDefaults(), Collections.<String>emptyList(),
                    true);
        }
        try {
            return Outcome.started(AppConfigLoader.load(file.path()));
        } catch (AppConfigException e) {
            if (ui == null) {
                return Outcome.failed(problems(file, e));
            }
            SettingsForm form;
            try {
                form = SettingsMapper.fromProperties(file.read());
            } catch (IOException io) {
                return Outcome.failed("Die Konfiguration unter\n" + file.path() + "\nist nicht lesbar ("
                        + io.getClass().getSimpleName() + ").");
            }
            return editUntilLoadable(file, ui, form, SettingsMapper.describe(e.problems()), false);
        }
    }

    private static Outcome editUntilLoadable(ConfigurationFile file, SettingsUi ui, SettingsForm form,
                                             List<String> problems, boolean firstStart) {
        SettingsForm current = form;
        List<String> currentProblems = problems;
        boolean first = firstStart;
        while (true) {
            SettingsForm edited = ui.edit(current, currentProblems, first);
            if (edited == null) {
                return Outcome.cancelled(first
                        ? "Die Einrichtung wurde abgebrochen; es wurde keine Konfiguration geschrieben (" + file.path()
                                + "). Der nächste Start öffnet den Dialog erneut."
                        : "Die Konfiguration unter\n" + file.path() + "\nwurde nicht korrigiert.");
            }
            try {
                return Outcome.started(AppConfigLoader.load(file.path()));
            } catch (AppConfigException e) {
                // Der Dialog speichert nur fehlerfreie Entwürfe; hier landet nur, was die Datei nach dem
                // Speichern trotzdem nicht laden lässt (z. B. parallel geändert).
                current = edited;
                currentProblems = SettingsMapper.describe(e.problems());
                first = false;
            }
        }
    }

    /** Die bisherige Meldung des headless Starts ohne Datei (unverändert für smokeStartFatJar). */
    static String templateCreated(ConfigurationFile file) {
        return "Es gab noch keine Konfiguration. Eine kommentierte Vorlage wurde angelegt unter\n"
                + file.path() + "\nBitte ausfüllen (Basis-URL, Modell, KeePass-Eintrag für den API-Key) "
                + "und die Anwendung neu starten.";
    }

    static String problems(ConfigurationFile file, AppConfigException e) {
        StringBuilder sb = new StringBuilder("Die Konfiguration unter\n").append(file.path()).append("\nhat Fehler:\n");
        for (String problem : e.problems()) {
            sb.append("  - ").append(problem).append('\n');
        }
        return sb.toString();
    }
}
