package com.aresstack.enterpriseai.app.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Findet {@code local-model-runtime-sidecar*.jar} ohne Eintrag in {@code models.local.sidecarJar}: neben dem
 * Client-Jar, im Anwendungsverzeichnis und im Modellverzeichnis, jeweils auch eine Ebene tiefer (das entpackte
 * Release-Zip liegt meist in einem Ordner {@code local-model-runtime-sidecar-<version>}). Bevorzugt wird der Fund
 * zur Version des Clients, sonst die höchste Version im Datei- oder Ordnernamen.
 */
public final class SidecarJarLocator {

    private static final Pattern JAR_NAME = Pattern.compile("(?i)local-model-runtime-sidecar.*\\.jar");
    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)+)");

    private SidecarJarLocator() {
    }

    /** Sucht an den Standardorten; {@code null}, wenn nichts gefunden wurde. */
    public static Path find(Path modelRoot) {
        List<Path> roots = new ArrayList<Path>();
        Path clientDirectory = clientDirectory();
        if (clientDirectory != null) {
            roots.add(clientDirectory);
        }
        roots.add(AppPaths.appDirectory());
        if (modelRoot != null) {
            roots.add(modelRoot);
        }
        Package pkg = SidecarJarLocator.class.getPackage();
        return find(roots, pkg == null ? null : pkg.getImplementationVersion());
    }

    static Path find(List<Path> roots, final String clientVersion) {
        List<Path> found = new ArrayList<Path>();
        for (Path root : roots) {
            collect(root, found);
            for (Path child : children(root)) {
                if (Files.isDirectory(child)) {
                    collect(child, found);
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        Collections.sort(found, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                boolean aMatches = matchesVersion(a, clientVersion);
                if (aMatches != matchesVersion(b, clientVersion)) {
                    return aMatches ? -1 : 1;
                }
                int byVersion = compareVersions(version(b), version(a));
                return byVersion != 0 ? byVersion : a.toString().compareTo(b.toString());
            }
        });
        return found.get(0);
    }

    private static void collect(Path directory, List<Path> found) {
        for (Path child : children(directory)) {
            if (JAR_NAME.matcher(child.getFileName().toString()).matches() && Files.isRegularFile(child)
                    && !found.contains(child)) {
                found.add(child);
            }
        }
    }

    private static List<Path> children(Path directory) {
        List<Path> result = new ArrayList<Path>();
        if (directory == null || !Files.isDirectory(directory)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                result.add(child.toAbsolutePath().normalize());
            }
        } catch (IOException | RuntimeException e) {
            return result;
        }
        return result;
    }

    private static boolean matchesVersion(Path jar, String clientVersion) {
        return clientVersion != null && !clientVersion.trim().isEmpty() && clientVersion.trim().equals(version(jar));
    }

    /** Version aus dem Dateinamen, sonst aus dem Ordnernamen; leer, wenn keine. */
    private static String version(Path jar) {
        String own = firstVersion(jar.getFileName().toString());
        if (!own.isEmpty() || jar.getParent() == null || jar.getParent().getFileName() == null) {
            return own;
        }
        String folder = jar.getParent().getFileName().toString();
        return folder.toLowerCase(Locale.ROOT).contains("sidecar") ? firstVersion(folder) : "";
    }

    private static String firstVersion(String name) {
        Matcher matcher = VERSION.matcher(name);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static int compareVersions(String a, String b) {
        String[] left = a.isEmpty() ? new String[0] : a.split("\\.");
        String[] right = b.isEmpty() ? new String[0] : b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            long l = i < left.length && left[i].length() < 10 ? Long.parseLong(left[i]) : 0;
            long r = i < right.length && right[i].length() < 10 ? Long.parseLong(right[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    /** Ordner des laufenden Client-Jars; {@code null} beim Start aus Klassenverzeichnissen ohne Jar. */
    private static Path clientDirectory() {
        try {
            CodeSource source = SidecarJarLocator.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(source.getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
