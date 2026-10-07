package com.aresstack.enterpriseai.architecture;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Was Gradle tatsächlich deklariert: Module, Produktionsabhängigkeiten und Klassenverzeichnisse.
 *
 * <p>Erzeugt von {@code :architecture-tests:writeArchitectureBuildModel}; der Pfad kommt über die
 * Systemeigenschaft {@value #BUILD_MODEL_PROPERTY}. Die Tests laufen deshalb nur über Gradle.
 */
final class BuildModel {

    static final String BUILD_MODEL_PROPERTY = "enterpriseai.architecture.buildModel";

    private final Set<String> modules;
    private final Map<String, Set<String>> projectDependencies;
    private final Map<String, Set<String>> externalDependencies;
    private final Map<String, List<File>> classDirectories;

    BuildModel(Set<String> modules,
               Map<String, Set<String>> projectDependencies,
               Map<String, Set<String>> externalDependencies,
               Map<String, List<File>> classDirectories) {
        this.modules = Collections.unmodifiableSet(new TreeSet<String>(modules));
        this.projectDependencies = Collections.unmodifiableMap(new TreeMap<String, Set<String>>(projectDependencies));
        this.externalDependencies = Collections.unmodifiableMap(new TreeMap<String, Set<String>>(externalDependencies));
        this.classDirectories = Collections.unmodifiableMap(new TreeMap<String, List<File>>(classDirectories));
    }

    static BuildModel load() {
        String path = System.getProperty(BUILD_MODEL_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalStateException("Systemeigenschaft " + BUILD_MODEL_PROPERTY
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

        Set<String> modules = split(properties.getProperty("modules"), ",");
        Map<String, Set<String>> projectDependencies = new TreeMap<String, Set<String>>();
        Map<String, Set<String>> externalDependencies = new TreeMap<String, Set<String>>();
        Map<String, List<File>> classDirectories = new TreeMap<String, List<File>>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("projectDependencies.")) {
                projectDependencies.put(name.substring("projectDependencies.".length()),
                        split(properties.getProperty(name), ","));
            } else if (name.startsWith("externalDependencies.")) {
                externalDependencies.put(name.substring("externalDependencies.".length()),
                        split(properties.getProperty(name), ","));
            } else if (name.startsWith("classDirectories.")) {
                List<File> directories = new ArrayList<File>();
                for (String directory : split(properties.getProperty(name), File.pathSeparator)) {
                    directories.add(new File(directory));
                }
                classDirectories.put(name.substring("classDirectories.".length()), directories);
            }
        }
        return new BuildModel(modules, projectDependencies, externalDependencies, classDirectories);
    }

    private static Set<String> split(String value, String separator) {
        Set<String> result = new LinkedHashSet<String>();
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

    /** Alle Gradle-Unterprojekte (inklusive architecture-tests). */
    Set<String> modules() {
        return modules;
    }

    /** Gescannte Module, also alle außer architecture-tests. */
    Set<String> scannedModules() {
        return classDirectories.keySet();
    }

    Set<String> projectDependenciesOf(String module) {
        Set<String> result = projectDependencies.get(module);
        return result == null ? Collections.<String>emptySet() : result;
    }

    Set<String> externalDependenciesOf(String module) {
        Set<String> result = externalDependencies.get(module);
        return result == null ? Collections.<String>emptySet() : result;
    }

    List<File> classDirectoriesOf(String module) {
        List<File> result = classDirectories.get(module);
        return result == null ? Collections.<File>emptyList() : result;
    }

    /** Nur existierende Klassenverzeichnisse (ein Modul ohne Quellen hat keines). */
    List<File> existingClassDirectoriesOf(String module) {
        List<File> result = new ArrayList<File>();
        for (File directory : classDirectoriesOf(module)) {
            if (directory.isDirectory()) {
                result.add(directory);
            }
        }
        return result;
    }
}
