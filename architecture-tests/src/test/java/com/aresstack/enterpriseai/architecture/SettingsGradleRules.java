package com.aresstack.enterpriseai.architecture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zweite, vom Gradle-Objektmodell unabhängige Quelle für die Modulliste: die {@code include}-Zeilen in
 * {@code settings.gradle}. Jedes dort eingetragene Modul muss in der {@link ModuleRegistry} stehen und
 * umgekehrt.
 */
final class SettingsGradleRules {

    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\s*\\(?\\s*['\"]:?([^'\"]+)['\"]", Pattern.MULTILINE);

    private SettingsGradleRules() {
    }

    static Set<String> includes(String settingsText) {
        Set<String> includes = new LinkedHashSet<String>();
        Matcher matcher = INCLUDE.matcher(settingsText);
        while (matcher.find()) {
            includes.add(matcher.group(1).trim());
        }
        return includes;
    }

    static Set<String> includes(File settingsFile) {
        try {
            return includes(new String(Files.readAllBytes(settingsFile.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("settings.gradle nicht lesbar: " + settingsFile, e);
        }
    }

    static List<String> unregisteredIncludes(Set<String> includes, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String include : includes) {
            if (!registry.contains(include)) {
                violations.add("settings.gradle enthält '" + include + "', ModuleRegistry nicht");
            }
        }
        return violations;
    }

    static List<String> registeredButNotIncluded(Set<String> includes, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String name : registry.names()) {
            if (!includes.contains(name)) {
                violations.add("ModuleRegistry enthält '" + name + "', settings.gradle nicht");
            }
        }
        return violations;
    }
}
