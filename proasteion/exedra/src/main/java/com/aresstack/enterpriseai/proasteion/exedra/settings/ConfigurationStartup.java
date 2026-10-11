package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

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
 * wird nach jeder Runde neu geladen, bis die Datei lädt (und die {@link ConfigurationCheck} besteht) oder der
 * Benutzer abbricht.
 *
 * <p>Mit Oberfläche und Einrichtungsassistent ({@link SetupUi}) gilt der Zustand {@value #SETUP_COMPLETED} im
 * Anwendungszustand: fehlt die Datei, öffnet sich immer der Assistent und der Zustand wird {@code false} (das hat
 * Vorrang vor einem alten {@code true}); nur ein erfolgreich beendeter Assistent, dessen Datei danach lädt, setzt
 * {@code true}. Abbruch oder Beenden während der Einrichtung lässt {@code false} stehen, sodass der nächste Start den
 * unterbrochenen Assistenten wieder öffnet. Eine vorhandene Datei ohne den Schlüssel ist eine bestehende Installation
 * und startet wie bisher.
 */
public final class ConfigurationStartup {

    /** Die Oberfläche, die den Entwurf bearbeiten lässt; {@code null} als Rückgabe heißt Abbruch. */
    public interface SettingsUi {
        SettingsForm edit(SettingsForm initial, List<String> problems, boolean firstStart);
    }

    /** Der Schlüssel des Einrichtungsstands im Anwendungszustand ({@code application-state.json}). */
    public static final String SETUP_COMPLETED = "setup.completed";

    /** Der Einrichtungsassistent; {@code true} heißt „Fertig“ nach bestandener Abschlussprüfung, sonst Abbruch. */
    public interface SetupUi {
        boolean run(SettingsForm initial);
    }

    /** Wo der Einrichtungsstand steht; produktiv der {@link ApplicationStateService}. */
    public interface SetupState {
        /** @return der gespeicherte Stand oder {@code null}, wenn der Schlüssel fehlt */
        Boolean completed();

        void setCompleted(boolean completed);
    }

    /** Der Einrichtungsstand im Anwendungszustand unter {@value #SETUP_COMPLETED}. */
    public static SetupState setupState(final ApplicationStateService state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        return new SetupState() {
            @Override
            public Boolean completed() {
                String value = state.get(SETUP_COMPLETED, null);
                return value == null ? null : Boolean.valueOf(value.trim());
            }

            @Override
            public void setCompleted(boolean completed) {
                state.putAndSave(SETUP_COMPLETED, Boolean.toString(completed));
            }
        };
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
        return obtain(file, ui, ConfigurationCheck.none());
    }

    /**
     * @param ui    der Dialog, oder {@code null} ohne Display (headless): dann Vorlage und Meldung wie bisher
     * @param check zusätzliche Prüfung der geladenen Konfiguration; ihre Probleme zählen wie die des Loaders
     */
    public static Outcome obtain(ConfigurationFile file, SettingsUi ui, ConfigurationCheck check) {
        if (file == null || check == null) {
            throw new IllegalArgumentException("file and check must not be null");
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
            return editUntilLoadable(file, ui, check, SettingsMapper.firstStartDefaults(),
                    Collections.<String>emptyList(), true);
        }
        try {
            return Outcome.started(load(file, check));
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
            return editUntilLoadable(file, ui, check, form, SettingsMapper.describe(e.problems()), false);
        }
    }

    /**
     * Wie {@link #obtain(ConfigurationFile, SettingsUi, ConfigurationCheck)}, mit Einrichtungsassistent nach den
     * Regeln der Klasse. Ohne Oberfläche ({@code ui == null}) oder ohne Assistent bleibt alles wie bisher.
     */
    public static Outcome obtain(ConfigurationFile file, SettingsUi ui, ConfigurationCheck check, SetupState state,
                                 SetupUi setup) {
        if (file == null || check == null) {
            throw new IllegalArgumentException("file and check must not be null");
        }
        if (ui == null || setup == null || state == null) {
            return obtain(file, ui, check);
        }
        if (!file.exists()) {
            state.setCompleted(false);
            return runSetup(file, ui, check, state, setup, SettingsMapper.firstStartDefaults());
        }
        if (Boolean.FALSE.equals(state.completed())) {
            SettingsForm form;
            try {
                form = SettingsMapper.fromProperties(file.read());
            } catch (IOException | RuntimeException unreadable) {
                form = SettingsMapper.firstStartDefaults();
            }
            if (form.chatApiKeyRef().isEmpty()) {
                form = form.toBuilder().chatApiKeyRef(SettingsMapper.firstStartDefaults().chatApiKeyRef()).build();
            }
            return runSetup(file, ui, check, state, setup, form);
        }
        return obtain(file, ui, check);
    }

    private static Outcome runSetup(ConfigurationFile file, SettingsUi ui, ConfigurationCheck check, SetupState state,
                                    SetupUi setup, SettingsForm initial) {
        if (!setup.run(initial)) {
            return Outcome.cancelled("Die Einrichtung wurde abgebrochen (" + file.path() + "). Der nächste Start "
                    + "öffnet den Einrichtungsassistenten erneut.");
        }
        try {
            AppConfig config = load(file, check);
            state.setCompleted(true);
            return Outcome.started(config);
        } catch (AppConfigException e) {
            // Die Abschlussprüfung des Assistenten lädt die Datei schon; landet hier, was danach nicht mehr lädt
            // (z. B. parallel geändert): reparieren wie bei einer fehlerhaften Datei.
            SettingsForm form;
            try {
                form = SettingsMapper.fromProperties(file.read());
            } catch (IOException io) {
                return Outcome.failed("Die Konfiguration unter\n" + file.path() + "\nist nicht lesbar ("
                        + io.getClass().getSimpleName() + ").");
            }
            Outcome repaired = editUntilLoadable(file, ui, check, form, SettingsMapper.describe(e.problems()), false);
            if (repaired.isStarted()) {
                state.setCompleted(true);
            }
            return repaired;
        }
    }

    private static AppConfig load(ConfigurationFile file, ConfigurationCheck check) {
        AppConfig config = AppConfigLoader.load(file.path());
        check.verify(config);
        return config;
    }

    private static Outcome editUntilLoadable(ConfigurationFile file, SettingsUi ui, ConfigurationCheck check,
                                             SettingsForm form, List<String> problems, boolean firstStart) {
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
                return Outcome.started(load(file, check));
            } catch (AppConfigException e) {
                // Der Dialog speichert nur fehlerfreie Entwürfe; hier landet nur, was die Datei nach dem
                // Speichern trotzdem nicht laden lässt (z. B. parallel geändert, Prüfung im Dialog ohne check).
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

    /** Die Probleme einer vorhandenen Datei als Meldung ohne Werte (auch für den Start nach dem Dialog). */
    public static String problems(ConfigurationFile file, AppConfigException e) {
        StringBuilder sb = new StringBuilder("Die Konfiguration unter\n").append(file.path()).append("\nhat Fehler:\n");
        for (String problem : e.problems()) {
            sb.append("  - ").append(problem).append('\n');
        }
        return sb.toString();
    }
}
