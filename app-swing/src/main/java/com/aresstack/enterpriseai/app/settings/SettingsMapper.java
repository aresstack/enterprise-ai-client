package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Übersetzt zwischen der Konfigurationsdatei ({@link Properties}) und dem {@link SettingsForm} des Dialogs.
 * Verwaltet nur die Schlüssel, die der Dialog zeigt; alle anderen Schlüssel der Datei bleiben unberührt.
 * Leere optionale Felder werden nicht als leerer Wert geschrieben, sondern entfernt (auskommentiert), damit die
 * Standardwerte des Loaders greifen und die Vorlage lesbar bleibt.
 */
public final class SettingsMapper {

    static final String KEY_WINDOW_TITLE = "ui.windowTitle";
    static final String KEY_CHAT_BASE_URL = "chat.baseUrl";
    static final String KEY_CHAT_MODEL = "chat.model";
    static final String KEY_CHAT_API_KEY_REF = "chat.apiKeyRef";
    static final String KEY_CHAT_SYSTEM_PROMPT = "chat.systemPrompt";
    static final String KEY_EMBEDDING_BASE_URL = "embedding.baseUrl";
    static final String KEY_EMBEDDING_MODEL = "embedding.model";
    static final String KEY_EMBEDDING_DIMENSION = "embedding.dimension";
    static final String KEY_EMBEDDING_API_KEY_REF = "embedding.apiKeyRef";
    static final String KEY_INDEX_DIRECTORY = "knowledge.indexDirectory";
    static final String KEY_INDEX_ON_STARTUP = "knowledge.indexOnStartup";
    static final String KEY_SOURCES = "sources";
    static final String KEY_KEEPASS_ENABLED = "security.keepass.enabled";
    static final String KEY_KEEPASS_HOST = "security.keepass.host";
    static final String KEY_KEEPASS_PORT = "security.keepass.port";
    static final String KEY_KEEPASS_DISPLAY_NAME = "security.keepass.clientDisplayName";
    static final String KEY_KEEPASS_PAIRING_STORE = "security.keepass.pairingKeyStore";
    static final String KEY_PROXY_MODE = "network.proxy.mode";
    static final String KEY_PAC_URL = "network.proxy.pacUrl";
    static final String KEY_PAC_DISCOVERY_SCRIPT = "network.proxy.pacDiscoveryScript";
    static final String KEY_PROXY_HOST = "network.proxy.host";
    static final String KEY_PROXY_PORT = "network.proxy.port";
    static final String KEY_TEST_URL = "network.proxy.testUrl";
    static final String KEY_RESOLVE_TIMEOUT = "network.proxy.resolveTimeoutMillis";
    static final String KEY_PROXY_AUTH_MODE = "network.proxy.auth.mode";
    static final String KEY_PROXY_CREDENTIAL_REF = "network.proxy.auth.credentialRef";
    static final String KEY_USER_AGENT = "network.http.userAgent";
    static final String KEY_PREFER_IPV6 = "network.http.preferIPv6";
    static final String KEY_TLS_JVM = "network.tls.useJvmDefault";
    static final String KEY_TLS_WINDOWS_ROOT = "network.tls.useWindowsRoot";
    static final String KEY_TLS_WINDOWS_CA = "network.tls.useWindowsCaStores";
    static final String KEY_TLS_CA_FILE = "network.tls.caCertificatesFile";
    /** Schlüssel der Fassungen bis 0.1.3: nur gelesen, beim Speichern entfernt. */
    static final String KEY_LEGACY_PAC_DISCOVERY = "network.proxy.pacDiscovery";
    static final String KEY_LEGACY_TLS_WINDOWS_STORE = "network.tls.useWindowsCertificateStore";
    static final String KEY_AGENT_ENABLED = "agent.enabled";
    static final String KEY_AGENT_COMMAND = "agent.command";
    static final String KEY_AGENT_ARGS = "agent.args";
    static final String KEY_AGENT_TIMEOUT = "agent.requestTimeoutSeconds";
    static final String SOURCE_PREFIX = "source.";

    private SettingsMapper() {
    }

    /**
     * Das Formular für den allerersten Start: leere Pflichtfelder und die Standardwerte des Loaders für den Rest.
     * Einzige Ausnahme ist der KeePass-Titel {@code keepass:Enterprise AI API}: ein Vorschlag, derselbe wie in der
     * Vorlage und in der Doku, damit der Eintrag in KeePass und die Konfiguration ohne Abtippen zusammenpassen.
     */
    public static SettingsForm firstStartDefaults() {
        return SettingsForm.builder().chatApiKeyRef("keepass:Enterprise AI API").build();
    }

    /** Liest die vom Dialog verwalteten Schlüssel; fehlende Schlüssel erscheinen leer bzw. mit Standardwert. */
    public static SettingsForm fromProperties(Properties p) {
        if (p == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        SettingsForm.Builder b = SettingsForm.builder();
        b.windowTitle(text(p, KEY_WINDOW_TITLE, "Enterprise AI Client"));
        b.chatBaseUrl(text(p, KEY_CHAT_BASE_URL, ""));
        b.chatModel(text(p, KEY_CHAT_MODEL, ""));
        b.chatApiKeyRef(text(p, KEY_CHAT_API_KEY_REF, ""));
        b.chatSystemPrompt(text(p, KEY_CHAT_SYSTEM_PROMPT, ""));
        b.embeddingBaseUrl(text(p, KEY_EMBEDDING_BASE_URL, ""));
        b.embeddingModel(text(p, KEY_EMBEDDING_MODEL, ""));
        b.embeddingDimension(text(p, KEY_EMBEDDING_DIMENSION, ""));
        b.embeddingApiKeyRef(text(p, KEY_EMBEDDING_API_KEY_REF, ""));
        b.indexDirectory(text(p, KEY_INDEX_DIRECTORY, ""));
        b.indexOnStartup(bool(p, KEY_INDEX_ON_STARTUP, true));
        for (String id : list(text(p, KEY_SOURCES, ""))) {
            SourceForm source = source(p, id);
            if (source != null) {
                b.addSource(source);
            }
        }
        b.keePassEnabled(bool(p, KEY_KEEPASS_ENABLED, true));
        b.keePassHost(text(p, KEY_KEEPASS_HOST, "127.0.0.1"));
        b.keePassPort(text(p, KEY_KEEPASS_PORT, "12546"));
        b.keePassClientDisplayName(text(p, KEY_KEEPASS_DISPLAY_NAME, "Enterprise AI Client"));
        b.keePassPairingKeyStore(text(p, KEY_KEEPASS_PAIRING_STORE, SettingsForm.PAIRING_KEY_STORE_FILE)
                .toLowerCase(Locale.ROOT));
        String pacUrl = text(p, KEY_PAC_URL, "");
        String mode = text(p, KEY_PROXY_MODE, SettingsForm.PROXY_PAC_URL_POWERSHELL).toUpperCase(Locale.ROOT)
                .replace('-', '_');
        Object legacy = AppConfigLoader.legacyMode(mode, pacUrl.isEmpty() ? null : pacUrl);
        b.proxyMode(legacy == null ? mode : legacy.toString());
        b.pacUrl(pacUrl);
        String script = p.getProperty(KEY_PAC_DISCOVERY_SCRIPT);
        b.pacDiscoveryScript(script == null ? "" : script);
        b.proxyHost(text(p, KEY_PROXY_HOST, ""));
        b.proxyPort(text(p, KEY_PROXY_PORT, ""));
        b.testUrl(text(p, KEY_TEST_URL, ""));
        b.resolveTimeoutMillis(text(p, KEY_RESOLVE_TIMEOUT, ""));
        b.proxyAuthMode(text(p, KEY_PROXY_AUTH_MODE, SettingsForm.PROXY_AUTH_NONE).toUpperCase(Locale.ROOT));
        b.proxyCredentialRef(text(p, KEY_PROXY_CREDENTIAL_REF, ""));
        b.userAgent(text(p, KEY_USER_AGENT, ""));
        b.preferIpv6(bool(p, KEY_PREFER_IPV6, false));
        boolean legacyWindows = bool(p, KEY_LEGACY_TLS_WINDOWS_STORE, true);
        b.tlsJvmDefault(bool(p, KEY_TLS_JVM, true));
        b.tlsWindowsRoot(bool(p, KEY_TLS_WINDOWS_ROOT, legacyWindows));
        b.tlsWindowsCaStores(bool(p, KEY_TLS_WINDOWS_CA, legacyWindows));
        b.caCertificatesFile(text(p, KEY_TLS_CA_FILE, ""));
        b.agentEnabled(bool(p, KEY_AGENT_ENABLED, false));
        b.agentCommand(text(p, KEY_AGENT_COMMAND, ""));
        b.agentArgs(text(p, KEY_AGENT_ARGS, ""));
        b.agentRequestTimeoutSeconds(text(p, KEY_AGENT_TIMEOUT, "30"));
        return b.build();
    }

    private static SourceForm source(Properties p, String id) {
        String prefix = SOURCE_PREFIX + id + ".";
        String type = text(p, prefix + "type", "").toLowerCase(Locale.ROOT);
        boolean wiki = SourceForm.TYPE_MEDIAWIKI.equals(type);
        boolean confluence = SourceForm.TYPE_CONFLUENCE.equals(type);
        if (!wiki && !confluence) {
            // Unbekannter oder fehlender Typ: der Dialog zeigt die Quelle als MediaWiki-Eintrag mit leerer URL,
            // damit der Benutzer sie sieht und korrigieren oder entfernen kann.
            wiki = true;
        }
        SourceForm.Builder b = SourceForm.builder(id, wiki ? SourceForm.TYPE_MEDIAWIKI : SourceForm.TYPE_CONFLUENCE)
                .url(text(p, prefix + (wiki ? "apiUrl" : "baseUrl"), ""))
                .credentialRef(text(p, prefix + "credentialRef", ""))
                .startPoints(text(p, prefix + "startPoints", ""))
                .maxDepth(text(p, prefix + "maxDepth", "1"))
                .maxResources(text(p, prefix + "maxResources", ""));
        if (wiki) {
            b.siteKey(text(p, prefix + "siteKey", ""))
                    .displayName(text(p, prefix + "displayName", ""))
                    .requiresLogin(bool(p, prefix + "requiresLogin", !text(p, prefix + "credentialRef", "").isEmpty()));
        } else {
            b.searchSpaceKeys(text(p, prefix + "searchSpaceKeys", ""))
                    .includeAttachments(bool(p, prefix + "includeAttachments", false));
        }
        return b.build();
    }

    /** Die Schlüssel, die das Formular setzt (in Dateireihenfolge der Vorlage). */
    public static Map<String, String> changes(SettingsForm form) {
        Map<String, String> set = new LinkedHashMap<String, String>();
        put(set, KEY_WINDOW_TITLE, form.windowTitle());
        put(set, KEY_CHAT_BASE_URL, form.chatBaseUrl());
        put(set, KEY_CHAT_MODEL, form.chatModel());
        put(set, KEY_CHAT_API_KEY_REF, form.chatApiKeyRef());
        put(set, KEY_CHAT_SYSTEM_PROMPT, form.chatSystemPrompt());
        put(set, KEY_EMBEDDING_BASE_URL, form.embeddingBaseUrl());
        put(set, KEY_EMBEDDING_MODEL, form.embeddingModel());
        put(set, KEY_EMBEDDING_DIMENSION, form.embeddingDimension());
        put(set, KEY_EMBEDDING_API_KEY_REF, form.embeddingApiKeyRef());
        put(set, KEY_INDEX_DIRECTORY, form.indexDirectory());
        set.put(KEY_INDEX_ON_STARTUP, String.valueOf(form.indexOnStartup()));
        List<String> ids = new ArrayList<String>();
        for (SourceForm source : form.sources()) {
            ids.add(source.id());
        }
        set.put(KEY_SOURCES, join(ids));
        for (SourceForm source : form.sources()) {
            String prefix = SOURCE_PREFIX + source.id() + ".";
            set.put(prefix + "type", source.type());
            put(set, prefix + (source.isConfluence() ? "baseUrl" : "apiUrl"), source.url());
            put(set, prefix + "credentialRef", source.credentialRef());
            put(set, prefix + "startPoints", source.startPoints());
            put(set, prefix + "maxDepth", source.maxDepth());
            put(set, prefix + "maxResources", source.maxResources());
            if (source.isConfluence()) {
                put(set, prefix + "searchSpaceKeys", source.searchSpaceKeys());
                set.put(prefix + "includeAttachments", String.valueOf(source.includeAttachments()));
            } else {
                put(set, prefix + "siteKey", source.siteKey());
                put(set, prefix + "displayName", source.displayName());
                set.put(prefix + "requiresLogin", String.valueOf(source.requiresLogin()));
            }
        }
        set.put(KEY_KEEPASS_ENABLED, String.valueOf(form.keePassEnabled()));
        put(set, KEY_KEEPASS_HOST, form.keePassHost());
        put(set, KEY_KEEPASS_PORT, form.keePassPort());
        put(set, KEY_KEEPASS_DISPLAY_NAME, form.keePassClientDisplayName());
        put(set, KEY_KEEPASS_PAIRING_STORE, form.keePassPairingKeyStore());
        put(set, KEY_PROXY_MODE, form.proxyMode());
        put(set, KEY_PAC_URL, form.pacUrl());
        // Skript unverändert (mehrzeilig); ConfigurationFile schreibt Zeilenumbrüche als \n.
        set.put(KEY_PAC_DISCOVERY_SCRIPT, form.pacDiscoveryScript() == null || form.pacDiscoveryScript().trim().isEmpty()
                ? "" : form.pacDiscoveryScript());
        put(set, KEY_PROXY_HOST, form.proxyHost());
        put(set, KEY_PROXY_PORT, form.proxyPort());
        put(set, KEY_TEST_URL, form.testUrl());
        put(set, KEY_RESOLVE_TIMEOUT, form.resolveTimeoutMillis());
        put(set, KEY_PROXY_AUTH_MODE, form.proxyAuthMode());
        put(set, KEY_PROXY_CREDENTIAL_REF, form.proxyCredentialRef());
        put(set, KEY_USER_AGENT, form.userAgent());
        set.put(KEY_PREFER_IPV6, String.valueOf(form.preferIpv6()));
        set.put(KEY_TLS_JVM, String.valueOf(form.tlsJvmDefault()));
        set.put(KEY_TLS_WINDOWS_ROOT, String.valueOf(form.tlsWindowsRoot()));
        set.put(KEY_TLS_WINDOWS_CA, String.valueOf(form.tlsWindowsCaStores()));
        put(set, KEY_TLS_CA_FILE, form.caCertificatesFile());
        set.put(KEY_AGENT_ENABLED, String.valueOf(form.agentEnabled()));
        put(set, KEY_AGENT_COMMAND, form.agentCommand());
        put(set, KEY_AGENT_ARGS, form.agentArgs());
        put(set, KEY_AGENT_TIMEOUT, form.agentRequestTimeoutSeconds());
        return set;
    }

    /** Die Schlüssel, die der Dialog wirklich schreibt: {@link #changes} ohne leere Werte (die sind Entfernungen). */
    public static Map<String, String> writes(SettingsForm form) {
        Map<String, String> writes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : changes(form).entrySet()) {
            if (!entry.getValue().isEmpty()) {
                writes.put(entry.getKey(), entry.getValue());
            }
        }
        return writes;
    }

    /**
     * Die Schlüssel, die entfernt (auskommentiert) werden: verwaltete Schlüssel mit leerem Wert sowie alle
     * {@code source.<id>.*} der Datei, deren Quelle das Formular nicht mehr kennt oder deren Typ sich geändert hat.
     * Quell-IDs dürfen Punkte enthalten; ein Schlüssel wird deshalb der längsten bekannten ID (Formular oder
     * {@code sources} der Datei) zugeordnet. Schlüssel zu Quellen, die nirgends gelistet sind, bleiben unberührt.
     */
    public static Set<String> removals(SettingsForm form, Properties current) {
        Set<String> remove = new LinkedHashSet<String>();
        Map<String, String> changes = changes(form);
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                remove.add(entry.getKey());
            }
        }
        Map<String, String> types = new LinkedHashMap<String, String>();
        for (SourceForm source : form.sources()) {
            types.put(source.id(), source.type());
        }
        if (current != null) {
            Set<String> knownIds = new LinkedHashSet<String>(types.keySet());
            knownIds.addAll(list(text(current, KEY_SOURCES, "")));
            for (String key : current.stringPropertyNames()) {
                String id = sourceIdOf(key, knownIds);
                if (id == null) {
                    continue;
                }
                String type = types.get(id);
                if (type == null) {
                    remove.add(key);
                } else {
                    String fileType = text(current, SOURCE_PREFIX + id + ".type", "").toLowerCase(Locale.ROOT);
                    if (!type.equals(fileType) && !changes.containsKey(key)) {
                        remove.add(key);
                    }
                }
            }
        }
        remove.add(KEY_LEGACY_PAC_DISCOVERY);
        remove.add(KEY_LEGACY_TLS_WINDOWS_STORE);
        remove.removeAll(nonEmptyKeys(changes));
        return remove;
    }

    /** Die längste bekannte Quell-ID, zu der {@code source.<id>.<feld>} gehört, sonst {@code null}. */
    static String sourceIdOf(String key, Collection<String> knownIds) {
        if (!key.startsWith(SOURCE_PREFIX)) {
            return null;
        }
        String best = null;
        for (String id : knownIds) {
            String prefix = SOURCE_PREFIX + id + ".";
            if (key.startsWith(prefix) && key.length() > prefix.length() && (best == null || id.length() > best.length())) {
                best = id;
            }
        }
        return best;
    }

    /** Die Datei nach dem Speichern, als Properties (für die Prüfung mit dem Loader). */
    public static Properties merge(Properties current, SettingsForm form) {
        Properties merged = new Properties();
        if (current != null) {
            for (String key : current.stringPropertyNames()) {
                merged.setProperty(key, current.getProperty(key));
            }
        }
        for (String key : removals(form, current)) {
            merged.remove(key);
        }
        for (Map.Entry<String, String> entry : changes(form).entrySet()) {
            if (entry.getValue().isEmpty()) {
                merged.remove(entry.getKey());
            } else {
                merged.setProperty(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /** Verständlicher Feldname zu einem Schlüssel des Loaders, z. B. für Problemmeldungen. */
    public static String labelFor(String key) {
        if (key == null) {
            return "";
        }
        if (key.startsWith(SOURCE_PREFIX)) {
            int dot = key.lastIndexOf('.');
            if (dot > SOURCE_PREFIX.length()) {
                String id = key.substring(SOURCE_PREFIX.length(), dot); // IDs dürfen Punkte enthalten
                String field = key.substring(dot + 1);
                String label = "apiUrl".equals(field) ? "API-URL" : "baseUrl".equals(field) ? "Basis-URL"
                        : "startPoints".equals(field) ? "Startpunkte" : "credentialRef".equals(field) ? "KeePass-Eintrag"
                        : "maxDepth".equals(field) ? "Tiefe" : "maxResources".equals(field) ? "Höchstzahl Seiten"
                        : "type".equals(field) ? "Typ" : field;
                return "Quelle „" + id + "“, " + label;
            }
        }
        switch (key) {
            case KEY_WINDOW_TITLE:
                return "Fenstertitel";
            case KEY_CHAT_BASE_URL:
                return "Basis-URL des KI-Dienstes";
            case KEY_CHAT_MODEL:
                return "Chat-Modell";
            case KEY_CHAT_API_KEY_REF:
                return "KeePass-Eintrag mit dem API-Key";
            case KEY_CHAT_SYSTEM_PROMPT:
                return "System-Prompt";
            case KEY_EMBEDDING_BASE_URL:
                return "Basis-URL für Embeddings";
            case KEY_EMBEDDING_MODEL:
                return "Embedding-Modell";
            case KEY_EMBEDDING_DIMENSION:
                return "Embedding-Dimension";
            case KEY_EMBEDDING_API_KEY_REF:
                return "KeePass-Eintrag für Embeddings";
            case KEY_INDEX_DIRECTORY:
                return "Indexverzeichnis";
            case KEY_SOURCES:
                return "Wissensquellen";
            case KEY_KEEPASS_HOST:
                return "KeePassRPC-Host";
            case KEY_KEEPASS_PORT:
                return "KeePassRPC-Port";
            case KEY_KEEPASS_PAIRING_STORE:
                return "Ablage des Pairing-Schlüssels";
            case KEY_PROXY_MODE:
                return "Mode";
            case KEY_PAC_URL:
                return "PAC URL";
            case KEY_PAC_DISCOVERY_SCRIPT:
                return "PAC URL discovery script";
            case KEY_PROXY_HOST:
                return "Manual host";
            case KEY_PROXY_PORT:
                return "Manual port";
            case KEY_TEST_URL:
                return "Test URL";
            case KEY_RESOLVE_TIMEOUT:
                return "Resolve timeout (ms)";
            case KEY_PROXY_AUTH_MODE:
                return "Proxy auth mode";
            case KEY_PROXY_CREDENTIAL_REF:
                return "Proxy credentials (KeePass entry)";
            case KEY_USER_AGENT:
                return "User-Agent";
            case KEY_TLS_CA_FILE:
                return "CA-Datei";
            case KEY_AGENT_COMMAND:
                return "Agent-Kommando";
            case KEY_AGENT_TIMEOUT:
                return "Agent-Timeout";
            default:
                return key;
        }
    }

    /** Problemzeile des Loaders ("schlüssel: Erwartung") mit Feldname davor. */
    public static String describe(String problem) {
        if (problem == null) {
            return "";
        }
        int colon = problem.indexOf(": ");
        if (colon <= 0) {
            return problem;
        }
        String key = problem.substring(0, colon);
        String label = labelFor(key);
        if (label.equals(key)) {
            return problem;
        }
        return label + " (" + key + "): " + problem.substring(colon + 2);
    }

    public static List<String> describe(List<String> problems) {
        List<String> described = new ArrayList<String>();
        if (problems != null) {
            for (String problem : problems) {
                described.add(describe(problem));
            }
        }
        return described;
    }

    // ------------------------------------------------------------------ Helfer

    private static void put(Map<String, String> set, String key, String value) {
        set.put(key, value == null ? "" : value.trim());
    }

    private static Set<String> nonEmptyKeys(Map<String, String> changes) {
        Set<String> keys = new LinkedHashSet<String>();
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    static String text(Properties p, String key, String def) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        return value.trim();
    }

    static boolean bool(Properties p, String key, boolean def) {
        String value = text(p, key, null);
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
        return def;
    }

    static List<String> list(String value) {
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

    static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(item);
        }
        return sb.toString();
    }
}
