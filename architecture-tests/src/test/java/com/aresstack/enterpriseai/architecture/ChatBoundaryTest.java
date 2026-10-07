package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Strang A: schärfere Grenzen für den Chat-Kern als die allgemeinen Kernregeln. Das Chat-Modell, der Port
 * und der Chat-Use-Case sehen nur einander und neutrale JDK-Typen, keine anderen Fähigkeiten und keinen
 * Adapter.
 */
public class ChatBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final String DOMAIN_CHAT = ROOT + ".domain.chat..";
    private static final String CHAT_API = ROOT + ".chat.api..";
    private static final String APPLICATION_CHAT = ROOT + ".application.chat..";
    private static final String[] JDK_BASICS = {"java.lang..", "java.util.."};

    private static JavaClasses productionClasses;

    @BeforeClass
    public static void importProductionClasses() {
        BuildModel model = BuildModel.load();
        List<File> directories = new ArrayList<File>();
        for (String module : Arrays.asList("domain", "chat-api", "application")) {
            directories.addAll(model.existingClassDirectoriesOf(module));
        }
        productionClasses = new ClassFileImporter().importPaths(BuildModelTest.toPaths(directories));
    }

    @Test
    public void chatDomainModelIsSelfContained() {
        assertRule(classes().that().resideInAPackage(DOMAIN_CHAT)
                .should().onlyDependOnClassesThat().resideInAnyPackage(concat(JDK_BASICS, DOMAIN_CHAT))
                .because("das Chat-Modell kennt nur sich selbst und java.lang/java.util"));
    }

    @Test
    public void chatPortSeesOnlyTheChatModel() {
        assertRule(classes().that().resideInAPackage(CHAT_API)
                .should().onlyDependOnClassesThat().resideInAnyPackage(concat(JDK_BASICS, DOMAIN_CHAT, CHAT_API))
                .because("der Chat-Port enthält keine Provider-, HTTP- oder JSON-Typen und keine anderen Fähigkeiten"));
    }

    @Test
    public void chatUseCaseSeesOnlyChatPortAndModel() {
        assertRule(classes().that().resideInAPackage(APPLICATION_CHAT)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(concat(JDK_BASICS, DOMAIN_CHAT, CHAT_API, APPLICATION_CHAT))
                .because("der Chat-Use-Case spricht nur über den Chat-Port mit dem Modell"));
    }

    private static void assertRule(ArchRule rule) {
        Violations.assertNone("Chat-Grenze verletzt",
                Violations.of(java.util.Collections.singletonList(rule.allowEmptyShould(false)), productionClasses));
    }

    private static String[] concat(String[] base, String... more) {
        String[] result = Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, result, base.length, more.length);
        return result;
    }
}
