package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.settings.ConfigurationFile;
import com.aresstack.enterpriseai.app.settings.FileJavaRuntimeSettingsStore;
import com.aresstack.enterpriseai.application.localruntime.JavaRuntimeSelectionService;
import com.aresstack.enterpriseai.model.sidecar.FileSystemJavaRuntimeDiscovery;
import com.aresstack.enterpriseai.model.sidecar.ProcessJavaRuntimeProbe;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdrahtet die Java-Erkennung für den Sidecar: Suchorte aus {@code JAVA_HOME}, {@code PATH} und den üblichen
 * Installationsverzeichnissen (Windows: Program Files je Hersteller, {@code %LOCALAPPDATA%\Programs},
 * {@code %USERPROFILE%\.jdks}, Scoop; sonst {@code /usr/lib/jvm} und {@code ~/.jdks}), gespeichert wird in
 * {@code models.local.java}.
 */
public final class JavaRuntimes {

    private static final long PROBE_TIMEOUT_MILLIS = 10000L;

    private JavaRuntimes() {
    }

    public static JavaRuntimeSelectionService selectionService(ConfigurationFile file) {
        ProcessJavaRuntimeProbe probe = new ProcessJavaRuntimeProbe(PROBE_TIMEOUT_MILLIS);
        return new JavaRuntimeSelectionService(new FileSystemJavaRuntimeDiscovery(path(System.getenv("JAVA_HOME")),
                pathEntries(System.getenv("PATH")), searchRoots(), probe), probe,
                new FileJavaRuntimeSettingsStore(file));
    }

    private static List<Path> pathEntries(String path) {
        List<Path> entries = new ArrayList<Path>();
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                add(entries, path(entry));
            }
        }
        return entries;
    }

    private static List<Path> searchRoots() {
        List<Path> roots = new ArrayList<Path>();
        for (String programFiles : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramW6432")}) {
            Path base = path(programFiles);
            if (base != null) {
                for (String vendor : new String[]{"Java", "Zulu", "Eclipse Adoptium", "Eclipse Foundation",
                        "Microsoft", "Amazon Corretto", "BellSoft", "RedHat", "SapMachine", "JetBrains",
                        "Semeru", "Oracle"}) {
                    add(roots, base.resolve(vendor));
                }
            }
        }
        Path programFilesX86 = path(System.getenv("ProgramFiles(x86)"));
        if (programFilesX86 != null) {
            add(roots, programFilesX86.resolve("Java"));
        }
        Path localAppData = path(System.getenv("LOCALAPPDATA"));
        if (localAppData != null) {
            add(roots, localAppData.resolve("Programs").resolve("Eclipse Adoptium"));
        }
        Path home = path(System.getProperty("user.home"));
        if (home != null) {
            add(roots, home.resolve(".jdks"));
            add(roots, home.resolve("scoop").resolve("apps"));
        }
        add(roots, path("/usr/lib/jvm"));
        return roots;
    }

    private static void add(List<Path> paths, Path path) {
        if (path != null && !paths.contains(path)) {
            paths.add(path);
        }
    }

    private static Path path(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Paths.get(text.trim());
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
