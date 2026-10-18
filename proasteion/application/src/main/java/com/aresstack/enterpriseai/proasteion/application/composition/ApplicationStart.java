package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.astu.acropolis.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ProxyAuthMode;
import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.proasteion.application.net.LiveNetwork;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.security.LiveSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.RetryingSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.proasteion.application.security.ProxyAuthenticator;
import com.aresstack.enterpriseai.proasteion.application.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Der Start ohne Oberfläche (corenth ADR-0001): Netzschicht (Proxy-Route, TLS-Vertrauen und User-Agent als
 * {@link NetworkServices}, ohne etwas aufzulösen; die erste Anfrage löst auf), Security-Port, Settings-Service,
 * Adapter ({@link ApplicationPorts}) und Graph ({@link CompositionRoot}), dazu die Beobachter, die gespeicherte
 * Einstellungen ohne Neustart übernehmen (Netz, Adapter, KeePass, Proxy-Anmeldung, Agent, Modellkatalog). Eine
 * Schale (exedra) ruft {@link #start} mit ihren Eingaben ({@link UserPrompts}) und baut darauf ihre Ansichten.
 * Prozessweit gesetzt werden nur {@code java.net.preferIPv6Addresses} (Option) und bei Proxy-Anmeldung BASIC der
 * {@link ProxyAuthenticator}.
 */
public final class ApplicationStart {

    private static final Logger LOG = Logger.getLogger(ApplicationStart.class.getName());

    private final LiveNetwork network;
    private final SettingsService settings;
    private final ApplicationPorts ports;
    private final CompositionRoot root;

    private ApplicationStart(LiveNetwork network, SettingsService settings, ApplicationPorts ports,
                             CompositionRoot root) {
        this.network = network;
        this.settings = settings;
        this.ports = ports;
        this.root = root;
    }

    /**
     * Setzt die Anwendung aus der geladenen Konfiguration zusammen.
     *
     * @param file          die Konfigurationsdatei (Neuladen nach dem Speichern)
     * @param config        die beim Start geladene Konfiguration
     * @param prompts       Eingaben der Oberfläche (Pairing-Dialog, Zugang „Manuell“)
     * @param localVoices   Stimmenversorgung (Proxy-Anmeldung der Downloads)
     * @param modelCatalogs Modellquellen, im Hintergrund nach jedem Start und jeder Änderung abgefragt
     * @param partitions    die angehakten Spaces je Quelle
     * @param uiExecutor    führt Änderungen an den Modellen der Schale aus (produktiv {@code SwingUtilities::invokeLater})
     * @param clock         Epoch-Millisekunden für Zeitstempel
     * @param zone          Zeitzone der Zeitstempel ({@code null}: Systemzone)
     */
    public static ApplicationStart start(final ConfigurationFile file, AppConfig config, final UserPrompts prompts,
                                         final LocalVoices localVoices, final ModelCatalogs modelCatalogs,
                                         SourcePartitionSelection partitions, Executor uiExecutor,
                                         LongSupplier clock, ZoneId zone)
            throws AppConfigException, IOException {
        NetworkConfig networkConfig = config.network();
        if (networkConfig.preferIpv6() && System.getProperty("java.net.preferIPv6Addresses") == null) {
            // Wie askai-java8: nur wirksam, bevor die erste Namensauflösung läuft.
            System.setProperty("java.net.preferIPv6Addresses", "true");
        }
        // Nichts wird prozessweit installiert: Route und Vertrauensregel bekommen die Adapter je Verbindung; die
        // erste Anfrage löst die Route auf und baut die Regel (PowerShell-Export unter Windows), nicht der Start.
        // Netzschicht und Security-Port übernehmen spätere Einstellungen ohne Neustart (siehe applySettings).
        final LiveNetwork network = new LiveNetwork(NetworkServices.from(networkConfig));
        LOG.info("Proxy-Regel: " + network.current().routes().describe());
        LOG.info("TLS-Vertrauensquellen (werden bei der ersten HTTPS-Verbindung geladen): "
                + networkConfig.describeTrust());
        LOG.info("User-Agent: " + network.current().userAgent());
        ProxyAuthenticator.allowBasicTunneling();
        // KeePass-Wiederholungen gelten ohne Neustart und ohne neue Anbindung (kein neues Pairing): der Decorator liest
        // die Regel je Zugriff aus diesem Halter, den das Laden der Einstellungen nachführt.
        final AtomicReference<RetryPolicy> keePassRetry = new AtomicReference<RetryPolicy>(config.keePass().retry());
        final LiveSecretProvider secrets = new LiveSecretProvider(vault(config, prompts, keePassRetry));
        // Zugang „KeePass“ ohne eingetragene URL: die Basis-URL steht im KeePass-Eintrag des API-Keys.
        config = AdapterAssembly.withVaultLocations(config, file.read(), secrets);
        final SettingsService settings = new SettingsService(config, settingsLoader(file, secrets, config, prompts,
                keePassRetry));
        final ApplicationPorts ports = AdapterAssembly.createWith(config, network, secrets, partitions,
                () -> settings.current().fileAccess().policy(), prompts.credentials().session());
        adoptSourceAddresses(file, ports, settings);
        applyProxyAuthentication(null, config, ports, localVoices);
        final CompositionRoot root = CompositionRoot.compose(settings, ports, uiExecutor, clock, zone);
        root.persistBrowserPolicyIn(file);
        final ApplicationStart started = new ApplicationStart(network, settings, ports, root);
        started.refreshModelsInBackground(modelCatalogs);
        // Beobachter auf dem Settings-Service (wie SettingsHelper.addChangeListener in MainframeMate): Adapter,
        // Netz, KeePass, Proxy-Anmeldung, Agent und Modellkatalog übernehmen gespeicherte Einstellungen sofort.
        settings.addListener(new SettingsService.Listener() {
            @Override
            public void settingsChanged(AppConfig previous, AppConfig current) {
                applySettings(previous, current, network, ports, root, localVoices);
                started.refreshModelsInBackground(modelCatalogs);
            }
        });
        return started;
    }

    /** Die Netzschicht von jetzt (Route, Vertrauen, User-Agent); folgt den Einstellungen. */
    public LiveNetwork network() {
        return network;
    }

    public SettingsService settings() {
        return settings;
    }

    public ApplicationPorts ports() {
        return ports;
    }

    public CompositionRoot root() {
        return root;
    }

    /** Das Bearer-Token des Chats, je Anfrage frisch: Eintrag aus den aktuellen Einstellungen, Wert aus KeePass. */
    public Supplier<String> chatToken() {
        return new Supplier<String>() {
            @Override
            public String get() {
                SecretRef ref = settings.current().chat().apiKeyRef();
                return ref == null ? null : new SecretBackedTokenSource(ports.secrets(), ref).token();
            }
        };
    }

    /**
     * Der KeePass- bzw. „Manuell“-Port nach den KeePass-Einstellungen, mit Pairing-Dialog für deren Adresse; lesende
     * Zugriffe wiederholt er, solange KeePassRPC vorübergehend nicht erreichbar ist ({@code security.keepass.retry*}).
     */
    private static SecretProvider vault(AppConfig config, UserPrompts prompts,
                                        final AtomicReference<RetryPolicy> retry) {
        return new RetryingSecretProvider(AdapterAssembly.secrets(config, prompts.pairing(
                config.keePass().rpc().host() + ":" + config.keePass().rpc().port()), prompts.credentials()),
                retry::get);
    }

    /**
     * Liest die gespeicherten Einstellungen wie der Start: Datei, bei geänderten KeePass-Einstellungen zuerst der
     * neue Security-Port, dann die Basis-URL aus KeePass (Zugang „KeePass“ ohne eingetragene URL).
     */
    private static SettingsService.Loader settingsLoader(final ConfigurationFile file,
                                                         final LiveSecretProvider secrets, AppConfig started,
                                                         final UserPrompts prompts,
                                                         final AtomicReference<RetryPolicy> keePassRetry) {
        final AtomicReference<String> keePass = new AtomicReference<String>(started.keePass().toString());
        return new SettingsService.Loader() {
            @Override
            public AppConfig load() throws IOException {
                Properties properties = file.read();
                AppConfig loaded = AppConfigLoader.fromProperties(properties);
                keePassRetry.set(loaded.keePass().retry());
                String vault = loaded.keePass().toString();
                if (!vault.equals(keePass.get())) {
                    secrets.replace(vault(loaded, prompts, keePassRetry));
                    keePass.set(vault);
                    LOG.info("KeePass-Anbindung neu: " + vault);
                }
                return AdapterAssembly.withVaultLocations(loaded, properties, secrets);
            }
        };
    }

    /**
     * Übernimmt gespeicherte Einstellungen in den laufenden Graphen: Netz (Proxy, PAC, TLS, User-Agent), Chat- und
     * Embedding-Adapter samt Modellauswahl, Indexverzeichnis, Proxy-Anmeldung; eine offene Agent-Sitzung endet,
     * wenn sich die Agent-Einstellungen geändert haben (die nächste Nachricht startet den Agenten neu).
     */
    private static void applySettings(AppConfig previous, AppConfig current, LiveNetwork network,
                                      ApplicationPorts ports, CompositionRoot root, LocalVoices localVoices) {
        network.apply(current.network());
        ports.apply(current, network.current());
        applyProxyAuthentication(previous, current, ports, localVoices);
        if (!previous.agent().toString().equals(current.agent().toString())) {
            try {
                root.agentService().endSession();
            } catch (IllegalStateException busy) {
                LOG.info("Agent arbeitet noch; neue Agent-Einstellungen gelten ab der nächsten Agent-Sitzung");
            }
        }
        if (previous.network().preferIpv6() != current.network().preferIpv6()) {
            // Die JVM liest java.net.preferIPv6Addresses nur vor der ersten Namensauflösung.
            LOG.info("IPv6 bevorzugen gilt erst beim nächsten Start (die JVM liest den Wert nur einmal)");
        }
        LOG.info("Einstellungen übernommen: " + current);
    }

    /** Proxy-Anmeldung BASIC aus KeePass setzen, ändern oder entfernen. */
    private static void applyProxyAuthentication(AppConfig previous, AppConfig current, ApplicationPorts ports,
                                                 LocalVoices localVoices) {
        NetworkConfig network = current.network();
        boolean basic = network.proxyAuthMode() == ProxyAuthMode.BASIC && network.proxyCredentialRef() != null;
        if (basic) {
            ProxyAuthenticator.install(ports.secrets(), network.proxyCredentialRef());
            localVoices.proxyAuthenticationInstalled();
        } else if (previous != null && previous.network().proxyAuthMode() == ProxyAuthMode.BASIC) {
            ProxyAuthenticator.uninstall();
        }
    }

    /** Markiert ein Indexverzeichnis, dessen Einträge schon Quellenadressen tragen (ab 0.1.104). */
    private static final String SOURCE_ADDRESS_INDEX_MARKER = ".source-addresses";

    /**
     * Löscht beim ersten Start mit Quellenadressen, was noch an frei vergebenen Quellen-IDs hängt: den ganzen
     * Wissensindex (Lucene und Vektoren, samt Passagen; er baut sich bei Bedarf neu auf) und die Space-Häkchen
     * {@code source.<id>.spaces.*} im Anwendungszustand. Kein Produktivbetrieb: keine Umschlüsselung, keine Aliase.
     * Favoriten mit alter ID verwirft der {@code FavoritesStore} beim Laden.
     */
    public static void dropLegacySourceEntries(Path indexDirectory, ApplicationStateService state) {
        if (indexDirectory != null) {
            Path root = indexDirectory.toAbsolutePath().normalize();
            Path marker = root.resolve(SOURCE_ADDRESS_INDEX_MARKER);
            try {
                if (!Files.exists(marker)) {
                    if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                        for (String part : new String[]{"text", "vectors"}) {
                            deleteRecursively(root.resolve(part));
                        }
                        LOG.info("Wissensindex mit alten Quellen-IDs gelöscht; er baut sich bei Bedarf neu auf");
                    }
                    Files.createDirectories(root);
                    Files.createFile(marker);
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Alter Wissensindex nicht gelöscht (" + e.getClass().getSimpleName() + ")", e);
            }
        }
        Map<String, String> stale = new LinkedHashMap<String, String>();
        for (String key : state.withPrefix("source.").keySet()) {
            if (key.contains(".spaces.") && !key.contains("://")) {
                stale.put(key, null);
            }
        }
        if (!stale.isEmpty()) {
            state.putAllAndSave(stale);
            LOG.info("Space-Häkchen alter Quellen-IDs gelöscht: " + stale.size());
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            List<Path> children = new ArrayList<Path>();
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    children.add(child);
                }
            }
            for (Path child : children) {
                deleteRecursively(child);
            }
        }
        Files.delete(path);
    }

    /**
     * Gleicht die Quellen der Konfiguration beim Start ab: Einträge der früheren Form mit frei vergebener ID
     * ({@code sources=…}, {@code source.<id>.*}) werden entfernt (kein Produktivbetrieb, keine Überführung; solche
     * Quellen legt der Benutzer neu an), von Hand eingetragene Quellen bekommen ihre Adresse aus Typ und Endpoint.
     * Danach liest die Anwendung die Konfiguration neu, damit Häkchen und Quellen dieselbe Adresse tragen.
     */
    private static void adoptSourceAddresses(ConfigurationFile file, ApplicationPorts ports,
                                             SettingsService settings) {
        try {
            FileSourceActions store = new FileSourceActions(file);
            int dropped = store.dropLegacyEntries();
            if (dropped > 0) {
                LOG.info("Wissensquellen der früheren Form (frei vergebene ID) entfernt: " + dropped
                        + " Schlüssel; bitte im Reiter „Wissensquellen“ neu anlegen");
            }
            Map<String, String> changed = new KnowledgeSourceManagement(ports.sourceProviders(), store,
                    ports.index()).adoptAddresses();
            if (!changed.isEmpty()) {
                LOG.info("Adressen der Wissensquellen aus Typ und Endpoint gesetzt: " + changed.values());
            }
            if (dropped > 0 || !changed.isEmpty()) {
                settings.reload();
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Wissensquellen nicht abgeglichen (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /**
     * Fragt die Modellquelle (KIPITZ {@code GET /models}) im Hintergrund ab, damit der Reiter „Modelle“ aktuelle
     * Listen hat; blockiert weder Start noch UI-Thread. Fehler landen nur im Protokoll.
     */
    private void refreshModelsInBackground(final ModelCatalogs catalogs) {
        final Supplier<String> token = chatToken();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    catalogs.refresh(settings.current(), network.current(), token);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Modellkatalog konnte nicht abgefragt werden", e);
                }
            }
        }, "enterprise-ai-model-catalog");
        thread.setDaemon(true);
        thread.start();
    }
}
