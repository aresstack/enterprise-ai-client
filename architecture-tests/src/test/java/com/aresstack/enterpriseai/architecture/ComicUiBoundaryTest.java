package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.app.ui.archfixture.UiCallingAdapter;
import com.aresstack.enterpriseai.app.ui.archfixture.UiCallingPort;
import com.aresstack.enterpriseai.app.ui.archfixture.UiOpeningHttp;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;
import com.aresstack.enterpriseai.chat.api.archfixture.PortWithMain;
import com.aresstack.enterpriseai.ui.comic.archfixture.ComicKnowingTheApp;
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
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Strang B (AP4): Grenzen der Comic-Oberfläche, zusätzlich zur Modulmatrix.
 *
 * <ul>
 *   <li>Die Oberfläche ({@code app.ui..}) erreicht Fachlogik nur über {@code application}: keine Adapter und
 *       keine Ports direkt, kein HTTP. Die Anbindung an Use Cases ({@code app.chat}) und die Composition Root
 *       liegen außerhalb von {@code app.ui}.</li>
 *   <li>{@code comic-controls} bleibt eine eigenständige Swing/Java2D-Bibliothek: nur JDK und eigene Typen.</li>
 * </ul>
 *
 * Swing außerhalb von app-swing/comic-controls verbietet bereits {@code ClassBoundaryTest.technologiesStayInTheirAdapter}.
 * Jede Regel hat hier einen Selbsttest mit absichtlichem Verstoß.
 */
public class ComicUiBoundaryTest {

    static final String UI_PACKAGE = ModuleRegistry.ROOT_PACKAGE + ".app.ui..";

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
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

    /** UI → nur application/domain/comic: Port- und Adapterpakete sind für {@code app.ui..} tabu. */
    static ArchRule uiReachesPortsAndAdaptersOnlyThroughApplication(ModuleRegistry registry) {
        List<ArchitectureModule> hidden = registry.modulesOfKind(ModuleKind.PORT, ModuleKind.ADAPTER,
                ModuleKind.TEST_FIXTURE);
        String[] packages = new String[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) {
            packages[i] = hidden.get(i).packagePattern();
        }
        return noClasses()
                .that().resideInAPackage(UI_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(packages)
                .because("die Oberfläche spricht Fachlogik nur über Application-Use-Cases an; "
                        + "Adapter verdrahtet allein die Composition Root")
                .allowEmptyShould(true);
    }

    static ArchRule uiOpensNoHttp() {
        return noClasses()
                .that().resideInAPackage(UI_PACKAGE)
                .should().dependOnClassesThat(Technology.HTTP.predicate())
                .because("HTTP gehört in Adapter, nicht in die Oberfläche")
                .allowEmptyShould(true);
    }

    static ArchRule comicControlsIsSelfContained(ModuleRegistry registry) {
        String comic = registry.module("comic-controls").packagePattern();
        return classes()
                .that().resideInAPackage(comic)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "javax..", comic)
                .because("comic-controls ist eine abhängigkeitsfreie Swing/Java2D-Bibliothek")
                .allowEmptyShould(true);
    }

    static List<ArchRule> rules(ModuleRegistry registry) {
        return Arrays.asList(uiReachesPortsAndAdaptersOnlyThroughApplication(registry), uiOpensNoHttp(),
                comicControlsIsSelfContained(registry));
    }

    @Test
    public void productionUiRespectsItsBoundaries() {
        Violations.assertNone("Grenzverletzung der Comic-Oberfläche", Violations.of(rules(REGISTRY), productionClasses));
    }

    @Test
    public void uiUsingAnAdapterOrAPortIsDetected() {
        JavaClasses classes = new ClassFileImporter().importClasses(UiCallingAdapter.class, UiCallingPort.class,
                FakeLuceneAdapter.class, PortWithMain.class);
        List<String> violations = Violations.of(
                java.util.Collections.singletonList(uiReachesPortsAndAdaptersOnlyThroughApplication(REGISTRY)),
                classes);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UiCallingAdapter"));
        assertTrue(violations.get(0), violations.get(0).contains("UiCallingPort"));
    }

    @Test
    public void uiOpeningHttpIsDetected() {
        List<String> violations = Violations.of(java.util.Collections.singletonList(uiOpensNoHttp()),
                new ClassFileImporter().importClasses(UiOpeningHttp.class));
        assertEquals(violations.toString(), 1, violations.size());
    }

    @Test
    public void comicControlsKnowingTheAppIsDetected() {
        List<String> violations = Violations.of(
                java.util.Collections.singletonList(comicControlsIsSelfContained(REGISTRY)),
                new ClassFileImporter().importClasses(ComicKnowingTheApp.class, UiCallingPort.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ComicKnowingTheApp"));
    }
}
