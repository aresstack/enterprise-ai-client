package com.aresstack.enterpriseai.architecture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auftrag: vollständige Java-8-Kompatibilität, keine Java-9+-APIs. Die API-Kompatibilität sichert
 * {@code javac --release 8} (Root-build.gradle); hier wird geprüft, dass jeder Kompilierschritt jedes Moduls
 * dieses Ziel wirklich erbt und dass alle erzeugten Klassendateien Bytecode-Major 52 (Java 8) tragen.
 */
final class Java8CompatibilityRules {

    static final String EXPECTED_ON_JDK_9_PLUS = "release=8";
    static final String EXPECTED_ON_JDK_8 = "target=1.8";

    private Java8CompatibilityRules() {
    }

    static boolean runningOnJdk9OrLater() {
        String version = System.getProperty("java.specification.version");
        return !version.startsWith("1.");
    }

    static List<String> compileTargetViolations(BuildModelExtension extension, boolean jdk9OrLater) {
        String expected = jdk9OrLater ? EXPECTED_ON_JDK_9_PLUS : EXPECTED_ON_JDK_8;
        List<String> violations = new ArrayList<String>();
        for (Map.Entry<String, Map<String, String>> module : extension.compileTasks().entrySet()) {
            if (!module.getValue().containsKey("compileJava")) {
                violations.add(module.getKey() + ": kein compileJava-Schritt im Build-Modell (Java-Plugin fehlt?)");
            }
            for (Map.Entry<String, String> task : module.getValue().entrySet()) {
                if (!expected.equals(task.getValue())) {
                    violations.add(module.getKey() + ":" + task.getKey() + " zielt auf " + task.getValue()
                            + " statt " + expected + " (Java-8-Konfiguration aus dem Root-build.gradle nicht geerbt)");
                }
            }
        }
        return violations;
    }

    static List<String> bytecodeViolations(List<File> classFiles) {
        List<String> violations = new ArrayList<String>();
        for (File classFile : classFiles) {
            ClassFileInfo info = ClassFileInfo.read(classFile);
            if (info.majorVersion() > ClassFileInfo.JAVA_8_MAJOR) {
                violations.add(info.className() + ": Bytecode-Version " + info.majorVersion() + " (Java 8 = 52): "
                        + classFile);
            }
        }
        return violations;
    }
}
