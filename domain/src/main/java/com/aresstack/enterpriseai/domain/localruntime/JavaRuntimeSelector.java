package com.aresstack.enterpriseai.domain.localruntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Auswahlregel für die Sidecar-Laufzeit: exakt Java 21 bevorzugen, sonst die kleinste kompatible höhere
 * Hauptversion; innerhalb einer Hauptversion die neueste Version, danach Hersteller und Pfad, damit die Wahl
 * deterministisch ist. Die höchste Version gewinnt nicht automatisch, weil der Sidecar Java 21 verlangt und neuere
 * JDKs Verhalten ändern können.
 */
public final class JavaRuntimeSelector {

    private static final Comparator<JavaRuntimeInstallation> PREFERENCE = new Comparator<JavaRuntimeInstallation>() {
        @Override
        public int compare(JavaRuntimeInstallation a, JavaRuntimeInstallation b) {
            if (a.isCompatible() != b.isCompatible()) {
                return a.isCompatible() ? -1 : 1;
            }
            if (a.majorVersion() != b.majorVersion()) {
                // Kompatible aufsteigend (21 vor 22 vor 25), zu alte absteigend (17 vor 11 vor 8).
                int byMajor = a.majorVersion() < b.majorVersion() ? -1 : 1;
                return a.isCompatible() ? byMajor : -byMajor;
            }
            int byVersion = compareVersions(b.version(), a.version());
            if (byVersion != 0) {
                return byVersion;
            }
            int byVendor = a.vendor().compareToIgnoreCase(b.vendor());
            if (byVendor != 0) {
                return byVendor;
            }
            return a.executable().toString().compareToIgnoreCase(b.executable().toString());
        }
    };

    /**
     * @return die automatisch zu wählende Installation oder {@code null}, wenn keine kompatible gefunden wurde
     *         (der Sidecar bleibt dann aus; das ist kein Fehler)
     */
    public JavaRuntimeInstallation selectDefault(List<JavaRuntimeInstallation> installations) {
        List<JavaRuntimeInstallation> sorted = sortForDisplay(installations);
        return sorted.isEmpty() || !sorted.get(0).isCompatible() ? null : sorted.get(0);
    }

    /** Reihenfolge für das Dropdown: die automatische Wahl zuerst, zu alte Versionen am Ende. */
    public List<JavaRuntimeInstallation> sortForDisplay(List<JavaRuntimeInstallation> installations) {
        List<JavaRuntimeInstallation> sorted = new ArrayList<JavaRuntimeInstallation>();
        if (installations != null) {
            for (JavaRuntimeInstallation installation : installations) {
                if (installation != null) {
                    sorted.add(installation);
                }
            }
        }
        Collections.sort(sorted, PREFERENCE);
        return sorted;
    }

    /** Vergleicht Versionstexte Zahl für Zahl ({@code 21.0.10} nach {@code 21.0.8}). */
    static int compareVersions(String a, String b) {
        String[] left = a.split("[^0-9]+");
        String[] right = b.split("[^0-9]+");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            long l = number(left, i);
            long r = number(right, i);
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    private static long number(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty() || parts[index].length() > 9) {
            return 0;
        }
        return Long.parseLong(parts[index]);
    }
}
