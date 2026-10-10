package com.aresstack.enterpriseai.domain.localruntime;

import java.nio.file.Path;

/**
 * Eine Java-Installation, deren Version durch Ausführen ermittelt wurde (nicht aus dem Ordnernamen geraten).
 * Der Sidecar braucht mindestens {@link #REQUIRED_MAJOR_VERSION}.
 */
public final class JavaRuntimeInstallation {

    /** Mindestversion des lokalen Sidecars. */
    public static final int REQUIRED_MAJOR_VERSION = 21;

    private final Path executable;
    private final String version;
    private final int majorVersion;
    private final String vendor;

    /**
     * @param executable   {@code java} bzw. {@code java.exe}
     * @param version      die gemeldete Version, z. B. {@code 21.0.8} oder {@code 1.8.0_481}
     * @param majorVersion die Hauptversion ({@code 1.8} → 8), 0 wenn unbekannt
     * @param vendor       der Hersteller ({@code java.vendor}); leer, wenn unbekannt
     */
    public JavaRuntimeInstallation(Path executable, String version, int majorVersion, String vendor) {
        if (executable == null) {
            throw new IllegalArgumentException("executable must not be null");
        }
        this.executable = executable;
        this.version = version == null || version.trim().isEmpty() ? "unbekannt" : version.trim();
        this.majorVersion = Math.max(0, majorVersion);
        this.vendor = vendor == null ? "" : vendor.trim();
    }

    /** Die Hauptversion aus einem Versionstext: {@code 1.8.0_481} → 8, {@code 21.0.8} → 21, sonst 0. */
    public static int majorOf(String version) {
        if (version == null) {
            return 0;
        }
        String text = version.trim();
        if (text.startsWith("1.")) {
            text = text.substring(2);
        }
        int end = 0;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end == 0 || end > 4) {
            return 0;
        }
        return Integer.parseInt(text.substring(0, end));
    }

    public Path executable() {
        return executable;
    }

    public String version() {
        return version;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public String vendor() {
        return vendor;
    }

    /** {@code true}, wenn der Sidecar mit dieser Installation läuft (Java 21 oder neuer). */
    public boolean isCompatible() {
        return majorVersion >= REQUIRED_MAJOR_VERSION;
    }

    /** Anzeigetext wie „Java 21.0.8 · Eclipse Adoptium“. */
    public String displayName() {
        return "Java " + version + (vendor.isEmpty() ? "" : " · " + vendor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaRuntimeInstallation)) {
            return false;
        }
        JavaRuntimeInstallation that = (JavaRuntimeInstallation) o;
        return executable.equals(that.executable) && version.equals(that.version)
                && majorVersion == that.majorVersion && vendor.equals(that.vendor);
    }

    @Override
    public int hashCode() {
        return executable.hashCode() * 31 + version.hashCode();
    }

    @Override
    public String toString() {
        return displayName() + " (" + executable + ")";
    }
}
