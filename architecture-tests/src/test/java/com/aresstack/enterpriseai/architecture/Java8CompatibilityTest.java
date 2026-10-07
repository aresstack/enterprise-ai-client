package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.domain.archfixture.NeutralValue;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Auftrag: vollständige Java-8-Kompatibilität. Jeder Kompilierschritt erbt {@code --release 8} (bzw. Target 1.8
 * auf JDK 8) und jede erzeugte Klassendatei (Produktion, Testfixtures, Tests aller Module) ist Java-8-Bytecode.
 */
public class Java8CompatibilityTest {

    @Test
    public void everyCompileStepOfEveryModuleTargetsJava8() {
        BuildModelExtension extension = BuildModelExtension.load();
        assertEquals("alle Module im Build-Modell", extension.modules().size(), extension.compileTasks().size());
        Violations.assertNone("Kompilierschritt ohne Java-8-Ziel",
                Java8CompatibilityRules.compileTargetViolations(extension, Java8CompatibilityRules.runningOnJdk9OrLater()));
    }

    @Test
    public void allClassFilesAreJava8Bytecode() {
        BuildModel model = BuildModel.load();
        BuildModelExtension extension = BuildModelExtension.load();
        List<File> directories = new ArrayList<File>();
        for (String module : model.scannedModules()) {
            directories.addAll(model.existingClassDirectoriesOf(module));
        }
        for (String module : extension.modulesWithTestFixtures()) {
            directories.addAll(extension.testFixtureClassDirectoriesOf(module));
        }
        Set<String> modulesWithTests = extension.modulesWithTests();
        assertTrue("Testklassen aller Module erwartet: " + modulesWithTests, modulesWithTests.containsAll(extension.modules()));
        for (String module : modulesWithTests) {
            directories.addAll(extension.testClassDirectoriesOf(module));
        }
        List<File> classFiles = ClassFileInfo.classFilesUnder(directories);
        assertTrue("zu wenige Klassendateien: " + classFiles.size(), classFiles.size() > 200);
        Violations.assertNone("Bytecode über Java 8", Java8CompatibilityRules.bytecodeViolations(classFiles));
    }

    @Test
    public void compileStepWithoutRelease8IsDetected() {
        Properties properties = new Properties();
        properties.setProperty("modules", "domain,chat-api");
        properties.setProperty("compileTasks.domain", "compileJava=release=11,compileTestJava=release=8");
        properties.setProperty("compileTasks.chat-api", "compileTestJava=release=8");
        List<String> violations = Java8CompatibilityRules.compileTargetViolations(new BuildModelExtension(properties), true);
        assertEquals(violations.toString(), 2, violations.size());
        assertTrue(violations.toString(), violations.toString().contains("domain:compileJava zielt auf release=11"));
        assertTrue(violations.toString(), violations.toString().contains("chat-api: kein compileJava-Schritt"));
        // Auf JDK 8 wird target=1.8 erwartet: beide domain-Schritte, der fehlende und der vorhandene chat-api-Schritt.
        assertEquals(4, Java8CompatibilityRules.compileTargetViolations(new BuildModelExtension(properties), false).size());
    }

    @Test
    public void newerBytecodeIsDetected() throws IOException {
        byte[] bytes = classBytes(NeutralValue.class);
        assertEquals(ClassFileInfo.JAVA_8_MAJOR, ClassFileInfo.read(bytes).majorVersion());
        bytes[6] = 0;
        bytes[7] = 61; // Java 17
        ClassFileInfo patched = ClassFileInfo.read(bytes);
        assertEquals(61, patched.majorVersion());
        assertEquals(NeutralValue.class.getName(), patched.className());
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class");
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
