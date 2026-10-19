package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.browser.BrowserPolicyStore;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.BrowserAccessConfig;
import com.aresstack.enterpriseai.proasteion.application.config.BrowserProfile;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Writes "always" answers of the browser prompt into the allow / block list of the active profile
 * ({@code browser.access.allow} / {@code .block} for Extern, {@code browser.access.internal.*} for Intern) of the
 * configuration file (read-modify-write, serialized) and reloads the settings so they apply at once.
 */
final class FileBrowserPolicyStore implements BrowserPolicyStore {

    private final ConfigurationFile file;
    private final SettingsService settings;

    FileBrowserPolicyStore(ConfigurationFile file, SettingsService settings) {
        if (file == null || settings == null) {
            throw new IllegalArgumentException("file and settings must not be null");
        }
        this.file = file;
        this.settings = settings;
    }

    @Override
    public void allow(BrowserProfile profile, String host) throws IOException {
        add(profile.allowKey(), host);
    }

    @Override
    public void block(BrowserProfile profile, String host) throws IOException {
        add(profile.blockKey(), host);
    }

    private synchronized void add(String key, String host) throws IOException {
        if (host == null || host.trim().isEmpty()) {
            return;
        }
        List<String> rules = BrowserAccessConfig.split(file.read().getProperty(key));
        if (!rules.contains(host)) {
            rules.add(host);
            file.update(Collections.singletonMap(key, BrowserAccessConfig.join(rules)),
                    Collections.<String>emptySet(), AppConfigLoader.exampleConfiguration());
        }
        settings.reload();
    }
}
