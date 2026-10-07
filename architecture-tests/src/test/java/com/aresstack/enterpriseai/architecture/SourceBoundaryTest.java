package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Strangspezifische Grenzen für Wissensquellen (Strang E, AP11/AP12):
 * <ul>
 *   <li>{@code source-api} kennt keinen Source-Adapter ({@code source-api ↛ source-*}).</li>
 *   <li>Die öffentliche API eines Source-Adapters zeigt keine Protokoll- oder Bibliothekstypen
 *       (JSON, HTML, HTTP, JWBF): Nur Konfiguration, Credential-Callback und der Port-Adapter sind sichtbar.</li>
 * </ul>
 */
public class SourceBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final List<String> SOURCE_ADAPTER_PACKAGES = Arrays.asList(
            ROOT + ".source.mediawiki", ROOT + ".source.confluence");
    private static final List<String> LEAKING_TYPE_PREFIXES = Arrays.asList(
            "com.google.gson.", "org.jsoup.", "net.sourceforge.jwbf.", "okhttp3.", "org.apache.http.",
            "org.apache.hc.", "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.CookieManager",
            "javax.net.ssl.HttpsURLConnection", "com.fasterxml.jackson.");

    private static JavaClasses productionClasses;

    @BeforeClass
    public static void importProductionClasses() {
        BuildModel model = BuildModel.load();
        List<File> directories = new ArrayList<File>();
        for (String module : model.scannedModules()) {
            directories.addAll(model.existingClassDirectoriesOf(module));
        }
        productionClasses = new ClassFileImporter().importPaths(BuildModelTest.toPaths(directories));
    }

    @Test
    public void sourceApiDoesNotKnowAnySourceAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage(ROOT + ".source.api..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".source.mediawiki..",
                        ROOT + ".source.confluence..")
                .allowEmptyShould(true);
        rule.check(productionClasses);
    }

    @Test
    public void publicApiOfSourceAdaptersExposesNoProtocolTypes() {
        ArchRule rule = classes().that().resideInAnyPackage(packagePatterns()).and().arePublic()
                .should(notExposeProtocolTypes())
                .allowEmptyShould(true);
        rule.check(productionClasses);
    }

    @Test
    public void ruleDetectsALeakingSignature() {
        JavaClasses fixture = new ClassFileImporter().importClasses(LeakingFixture.class);
        if (!classes().should(notExposeProtocolTypes()).evaluate(fixture).hasViolation()) {
            throw new AssertionError("Regel erkennt java.net.HttpURLConnection in öffentlicher Signatur nicht");
        }
    }

    private static String[] packagePatterns() {
        String[] patterns = new String[SOURCE_ADAPTER_PACKAGES.size()];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = SOURCE_ADAPTER_PACKAGES.get(i) + "..";
        }
        return patterns;
    }

    private static ArchCondition<JavaClass> notExposeProtocolTypes() {
        return new ArchCondition<JavaClass>("expose no protocol or library types in public signatures") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCodeUnit unit : javaClass.getCodeUnits()) {
                    if (!isVisible(unit.getModifiers()) || unit.getName().startsWith("lambda$")) {
                        continue;
                    }
                    List<JavaClass> types = new ArrayList<JavaClass>(unit.getRawParameterTypes());
                    types.add(unit.getRawReturnType());
                    types.addAll(unit.getExceptionTypes());
                    for (JavaClass type : types) {
                        report(events, javaClass, unit.getFullName(), type);
                    }
                }
                for (JavaField field : javaClass.getFields()) {
                    if (isVisible(field.getModifiers())) {
                        report(events, javaClass, field.getFullName(), field.getRawType());
                    }
                }
            }
        };
    }

    private static boolean isVisible(java.util.Set<JavaModifier> modifiers) {
        return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
    }

    private static void report(ConditionEvents events, JavaClass owner, String member, JavaClass type) {
        String name = type.getName();
        for (String prefix : LEAKING_TYPE_PREFIXES) {
            if (name.startsWith(prefix)) {
                events.add(SimpleConditionEvent.violated(owner, member + " exposes " + name));
            }
        }
    }

    /** Absichtlicher Verstoß für den Selbsttest der Regel. */
    public static final class LeakingFixture {

        public java.net.HttpURLConnection connection() {
            return null;
        }
    }
}
