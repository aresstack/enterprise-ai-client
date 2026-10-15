package com.aresstack.enterpriseai.proasteion.application.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ablageorte im Benutzerverzeichnis. Übernommen aus askai-java8 {@code AskAiPaths}: Anwendungsverzeichnis unter
 * {@code %APPDATA%} (Windows) bzw. {@code user.home}, überschreibbar per System-Property.
 *
 * <ul>
 *   <li>{@code -Denterpriseai.home=<Verzeichnis>}: Anwendungsverzeichnis (Index, Pairing-Schlüssel, Konfiguration,
 *       Protokolldateien).</li>
 *   <li>{@code -Denterpriseai.config=<Datei>}: Konfigurationsdatei unabhängig vom Anwendungsverzeichnis.</li>
 * </ul>
 */
public final class AppPaths {

    public static final String HOME_PROPERTY = "enterpriseai.home";
    public static final String CONFIG_PROPERTY = "enterpriseai.config";
    public static final String APP_DIRECTORY_NAME = ".enterprise-ai-client";
    public static final String CONFIG_FILE_NAME = "enterprise-ai-client.properties";
    public static final String INDEX_DIRECTORY_NAME = "index";
    public static final String FACT_DIRECTORY_NAME = "facts";
    public static final String PAIRING_KEY_FILE_NAME = "keepassrpc-pairing.key";
    public static final String LOG_DIRECTORY_NAME = "logs";
    public static final String GIT_CACHE_DIRECTORY_NAME = "git-cache";
    /** Zwischenspeicher der letzten Modellabfrage (Reiter „Modelle“). */
    public static final String MODEL_CATALOG_FILE_NAME = "model-catalog.properties";
    /** Anwendungszustand der Oberfläche (Fenster, Drawer), getrennt von der Konfiguration. */
    public static final String APPLICATION_STATE_FILE_NAME = "application-state.json";

    private AppPaths() {
    }

    /** Das Anwendungsverzeichnis des Benutzers (wird nicht angelegt). */
    public static Path appDirectory() {
        String home = System.getProperty(HOME_PROPERTY);
        if (home != null && !home.trim().isEmpty()) {
            return Paths.get(home.trim());
        }
        String appData = System.getenv("APPDATA");
        String base = appData != null && !appData.trim().isEmpty() ? appData : System.getProperty("user.home");
        return Paths.get(base, APP_DIRECTORY_NAME);
    }

    /** Die Konfigurationsdatei: {@code -Denterpriseai.config} oder {@code <appDirectory>/enterprise-ai-client.properties}. */
    public static Path configFile() {
        String explicit = System.getProperty(CONFIG_PROPERTY);
        if (explicit != null && !explicit.trim().isEmpty()) {
            return Paths.get(explicit.trim());
        }
        return appDirectory().resolve(CONFIG_FILE_NAME);
    }

    public static Path defaultIndexDirectory() {
        return appDirectory().resolve(INDEX_DIRECTORY_NAME);
    }

    /** The global metadata catalog (#106): {@code <appDirectory>/facts/facts.mv.db}. */
    public static Path defaultFactDirectory() {
        return appDirectory().resolve(FACT_DIRECTORY_NAME);
    }

    public static Path defaultPairingKeyFile() {
        return appDirectory().resolve(PAIRING_KEY_FILE_NAME);
    }

    /** Protokolldateien: {@code <appDirectory>/logs/enterprise-ai-client.<n>.log}. */
    public static Path defaultLogDirectory() {
        return appDirectory().resolve(LOG_DIRECTORY_NAME);
    }

    /** Bare-Kopien der Git-Quellen (#137): {@code <appDirectory>/git-cache/<hash>}; gehört der Anwendung. */
    public static Path defaultGitCacheDirectory() {
        return appDirectory().resolve(GIT_CACHE_DIRECTORY_NAME);
    }

    /** Der Anwendungszustand liegt neben der Konfigurationsdatei (wie die Chat-Historie). */
    public static Path applicationStateFile() {
        return configFile().toAbsolutePath().getParent().resolve(APPLICATION_STATE_FILE_NAME);
    }
}
