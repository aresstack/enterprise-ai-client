package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die laufenden Einstellungen der Anwendung, wie der {@code SettingsHelper} in MainframeMate: genau eine Instanz aus
 * der Composition Root, überall durchgereicht. Verbraucher lesen den Wert über {@link #current()} bzw. {@link #get}
 * immer frisch statt ihn beim Start in ein Feld zu kopieren; wo ein Wert in ein gebautes Objekt geht (Adapter,
 * Netzroute, Fenstertitel), meldet sich der Verbraucher als {@link Listener} und wendet die Änderung an. Nach dem
 * Speichern lädt {@link #reload()} die Datei neu und benachrichtigt alle Beobachter; ein Neustart ist nicht nötig.
 */
public final class SettingsService {

    private static final Logger LOG = Logger.getLogger(SettingsService.class.getName());

    /** Liest die gespeicherten Einstellungen (produktiv: die Konfigurationsdatei samt KeePass-Ergänzungen). */
    public interface Loader {
        AppConfig load() throws IOException;
    }

    /** Wird nach jedem {@link #reload()} mit dem alten und dem neuen Stand gerufen (im Thread des Aufrufers). */
    public interface Listener {
        void settingsChanged(AppConfig previous, AppConfig current);
    }

    private final Loader loader;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private volatile AppConfig current;

    public SettingsService(AppConfig initial, Loader loader) {
        if (initial == null || loader == null) {
            throw new IllegalArgumentException("initial and loader must not be null");
        }
        this.current = initial;
        this.loader = loader;
    }

    /** Feste Einstellungen ohne Datei (Tests, Demos): {@link #reload()} liefert immer denselben Stand. */
    public static SettingsService fixed(final AppConfig config) {
        return new SettingsService(config, new Loader() {
            @Override
            public AppConfig load() {
                return config;
            }
        });
    }

    /** Der aktuelle Stand; nie in ein Feld kopieren, sondern bei jeder Verwendung neu fragen. */
    public AppConfig current() {
        return current;
    }

    /** Ein Wert als Funktion: {@code settings.get(c -> c.chat().systemPrompt())} liest bei jedem Aufruf frisch. */
    public <T> Supplier<T> get(final java.util.function.Function<AppConfig, T> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return new Supplier<T>() {
            @Override
            public T get() {
                return value.apply(current);
            }
        };
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Lädt die gespeicherten Einstellungen neu und benachrichtigt die Beobachter. Ein Beobachter, der scheitert,
     * hält die übrigen nicht auf; sein Fehler steht im Protokoll.
     *
     * @throws IOException wenn die Datei nicht lesbar ist (der bisherige Stand bleibt)
     * @throws com.aresstack.enterpriseai.app.config.AppConfigException wenn sie ungültig ist (ebenso)
     */
    public AppConfig reload() throws IOException {
        AppConfig loaded = loader.load();
        if (loaded == null) {
            throw new IllegalStateException("loader returned no configuration");
        }
        AppConfig previous;
        synchronized (this) {
            previous = current;
            current = loaded;
        }
        for (Listener listener : listeners) {
            try {
                listener.settingsChanged(previous, loaded);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Einstellungen nicht übernommen von " + listener, e);
            }
        }
        return loaded;
    }
}
