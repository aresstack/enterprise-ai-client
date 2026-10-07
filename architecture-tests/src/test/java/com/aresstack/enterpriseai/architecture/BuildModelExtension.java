package com.aresstack.enterpriseai.architecture;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * AP24-Erweiterungen des Build-Modells, additiv zu {@link BuildModel} aus derselben Properties-Datei:
 * Wurzelverzeichnis, Testfixture-Klassen und -Abhängigkeiten, exportierte Fremdbibliotheken ({@code api})
 * und das Java-Ziel jedes Kompilierschritts.
 */
final class BuildModelExtension {

    private final Properties properties;

    BuildModelExtension(Properties properties) {
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    static BuildModelExtension load() {
        String path = System.getProperty(BuildModel.BUILD_MODEL_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalStateException("Systemeigenschaft " + BuildModel.BUILD_MODEL_PROPERTY
                    + " fehlt; Architekturtests bitte über './gradlew :architecture-tests:test' ausführen.");
        }
        Properties properties = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Build-Modell nicht lesbar: " + path, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Lesefehler wurde bereits gemeldet oder Daten sind vollständig gelesen.
                }
            }
        }
        return new BuildModelExtension(properties);
    }

    /** Alle Gradle-Unterprojekte (inklusive architecture-tests). */
    Set<String> modules() {
        return list("modules", ",");
    }

    File rootDir() {
        String value = properties.getProperty("rootDir");
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("rootDir fehlt im Build-Modell");
        }
        return new File(value.trim());
    }

    /** Module mit einem {@code test}-Source-Set (inklusive architecture-tests). */
    Set<String> modulesWithTests() {
        return modulesWithPrefix("testClassDirectories.");
    }

    /** Kompilierte Testklassen eines Moduls (nur vorhandene Verzeichnisse). */
    List<File> testClassDirectoriesOf(String module) {
        return existingDirectories(list("testClassDirectories." + module, File.pathSeparator));
    }

    private Set<String> modulesWithPrefix(String prefix) {
        Set<String> result = new TreeSet<String>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                result.add(name.substring(prefix.length()));
            }
        }
        return result;
    }

    /** Module mit einem {@code testFixtures}-Source-Set. */
    Set<String> modulesWithTestFixtures() {
        return modulesWithPrefix("testFixtureClassDirectories.");
    }

    List<File> testFixtureClassDirectoriesOf(String module) {
        return existingDirectories(list("testFixtureClassDirectories." + module, File.pathSeparator));
    }

    /** Module, deren Testfixtures aus einer Produktionskonfiguration angefordert werden. */
    Set<String> testFixtureDependenciesOf(String module) {
        return list("testFixtureDependencies." + module, ",");
    }

    /** Fremdbibliotheken, die ein Modul mit {@code api}/{@code compileOnlyApi} exportiert. */
    Set<String> exportedExternalDependenciesOf(String module) {
        return list("exportedExternalDependencies." + module, ",");
    }

    /** Kompilierschritt → Ziel, z. B. {@code compileJava → release=8}. */
    Map<String, String> compileTasksOf(String module) {
        Map<String, String> result = new TreeMap<String, String>();
        for (String entry : list("compileTasks." + module, ",")) {
            int separator = entry.indexOf('=');
            if (separator > 0) {
                result.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Alle Module mit mindestens einem Kompilierschritt. */
    Map<String, Map<String, String>> compileTasks() {
        Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
        for (String module : new TreeSet<String>(modules())) {
            result.put(module, compileTasksOf(module));
        }
        return result;
    }

    private Set<String> list(String key, String separator) {
        Set<String> result = new LinkedHashSet<String>();
        String value = properties.getProperty(key);
        if (value == null) {
            return result;
        }
        for (String part : value.split(java.util.regex.Pattern.quote(separator))) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static List<File> existingDirectories(Set<String> paths) {
        List<File> result = new ArrayList<File>();
        for (String path : paths) {
            File directory = new File(path);
            if (directory.isDirectory()) {
                result.add(directory);
            }
        }
        return result;
    }
}
