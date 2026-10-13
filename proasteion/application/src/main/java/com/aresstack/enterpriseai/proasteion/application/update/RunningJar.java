package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The fat jar this application was started from, found through the code source of a class, never by an assumed
 * file name. Started from an IDE, class directories or the module jars of a Gradle run, there is no such jar:
 * {@link #isAvailable()} is false and {@link #reason()} says why.
 */
public final class RunningJar {

    /** {@code Implementation-Title} of the fat jar (gradle/fat-jar.gradle). */
    public static final String FAT_JAR_TITLE = "enterprise-ai-client";

    private final File file;
    private final String version;
    private final String reason;

    private RunningJar(File file, String version, String reason) {
        this.file = file;
        this.version = version;
        this.reason = reason;
    }

    /** The jar that holds {@code anchor}. */
    public static RunningJar of(Class<?> anchor) {
        File location;
        try {
            CodeSource source = anchor.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return unavailable("Der Startort der Anwendung ist nicht bekannt.");
            }
            location = new File(source.getLocation().toURI());
        } catch (Exception ex) {
            return unavailable("Der Startort der Anwendung ist nicht bekannt: " + ex.getMessage());
        }
        return at(location);
    }

    /** The fat jar at {@code location}, or unavailable when it is no readable fat jar. */
    public static RunningJar at(File location) {
        if (!location.isFile() || !location.getName().endsWith(".jar")) {
            return unavailable("Die Anwendung läuft nicht aus einem Fat Jar (Start aus Entwicklungsumgebung oder"
                    + " Klassenverzeichnis); ein Update braucht die ausgelieferte Jar-Datei als Basis.");
        }
        if (!location.canRead()) {
            return unavailable("Die gestartete Jar-Datei ist nicht lesbar: " + location.getAbsolutePath());
        }
        try (JarFile jar = new JarFile(location)) {
            Manifest manifest = jar.getManifest();
            Attributes attributes = manifest == null ? null : manifest.getMainAttributes();
            String title = attributes == null ? null : attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            String version = attributes == null ? null : attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (!FAT_JAR_TITLE.equals(title) || version == null || version.trim().isEmpty()) {
                return unavailable("Die gestartete Jar-Datei ist kein ausgeliefertes Fat Jar: "
                        + location.getName());
            }
            return new RunningJar(location.getAbsoluteFile(), version.trim(), "");
        } catch (IOException ex) {
            return unavailable("Die gestartete Jar-Datei ist nicht lesbar: " + ex.getMessage());
        }
    }

    private static RunningJar unavailable(String reason) {
        return new RunningJar(null, "", reason);
    }

    public boolean isAvailable() {
        return file != null;
    }

    /** The jar file; null when unavailable. */
    public File file() {
        return file;
    }

    /** {@code Implementation-Version} of the jar; empty when unavailable. */
    public String version() {
        return version;
    }

    /** Why no fat jar is available; empty when available. */
    public String reason() {
        return reason;
    }
}
