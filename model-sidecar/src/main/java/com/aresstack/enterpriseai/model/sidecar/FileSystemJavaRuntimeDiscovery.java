package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;
import com.aresstack.enterpriseai.model.api.JavaRuntimeDiscovery;
import com.aresstack.enterpriseai.model.api.JavaRuntimeProbe;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sucht Java wie das PowerShell-Startskript: zuerst {@code JAVA_HOME}, dann jeder {@code PATH}-Eintrag, dann die
 * üblichen Installationsverzeichnisse (je Wurzel die Unterordner und eine Ebene tiefer, z. B.
 * {@code scoop\apps\temurin21\current}), nie rekursiv. Jeder Kandidat wird mit dem {@link JavaRuntimeProbe}
 * ausgeführt. Welche Orte durchsucht werden, gibt die Composition Root vor (Umgebungsvariablen liest dieses Modul
 * nicht).
 */
public final class FileSystemJavaRuntimeDiscovery implements JavaRuntimeDiscovery {

    private final Path javaHome;
    private final List<Path> pathEntries;
    private final List<Path> searchRoots;
    private final JavaRuntimeProbe probe;

    /**
     * @param javaHome    {@code JAVA_HOME} oder {@code null}
     * @param pathEntries die Einträge von {@code PATH}
     * @param searchRoots Installationsverzeichnisse wie {@code C:\Program Files\Eclipse Adoptium}
     */
    public FileSystemJavaRuntimeDiscovery(Path javaHome, List<Path> pathEntries, List<Path> searchRoots,
                                          JavaRuntimeProbe probe) {
        if (probe == null) {
            throw new IllegalArgumentException("probe must not be null");
        }
        this.javaHome = javaHome;
        this.pathEntries = pathEntries == null ? Collections.<Path>emptyList() : new ArrayList<Path>(pathEntries);
        this.searchRoots = searchRoots == null ? Collections.<Path>emptyList() : new ArrayList<Path>(searchRoots);
        this.probe = probe;
    }

    @Override
    public List<JavaRuntimeInstallation> discover() {
        Map<String, Path> candidates = new LinkedHashMap<String, Path>();
        add(candidates, javaHome);
        for (Path entry : pathEntries) {
            // PATH zeigt auf das bin-Verzeichnis (oder Oracles javapath), nicht auf die Installation.
            if (entry != null) {
                add(candidates, entry.resolve("java.exe"));
                add(candidates, entry.resolve("java"));
            }
        }
        for (Path root : searchRoots) {
            for (Path child : directories(root)) {
                add(candidates, child);
                for (Path nested : directories(child)) {
                    add(candidates, nested);
                }
            }
        }
        List<JavaRuntimeInstallation> found = new ArrayList<JavaRuntimeInstallation>();
        for (Path executable : candidates.values()) {
            JavaRuntimeInstallation installation = probe.inspect(executable);
            if (installation != null) {
                found.add(installation);
            }
        }
        return found;
    }

    /** {@code location} ist {@code java(.exe)} selbst oder ein Installationsverzeichnis mit {@code bin/java(.exe)}. */
    private static void add(Map<String, Path> candidates, Path location) {
        Path executable = ProcessJavaRuntimeProbe.resolveExecutable(location);
        if (executable != null) {
            // Symbolische Links (/usr/bin/java, alternatives) nur einmal prüfen; der erste Fundort bleibt.
            String key = realPath(executable).toString().toLowerCase(Locale.ROOT);
            if (!candidates.containsKey(key)) {
                candidates.put(key, executable);
            }
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return path;
        }
    }

    private static List<Path> directories(Path root) {
        List<Path> result = new ArrayList<Path>();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    result.add(child);
                }
            }
        } catch (IOException | RuntimeException e) {
            return result;
        }
        Collections.sort(result);
        return result;
    }
}
