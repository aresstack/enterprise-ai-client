package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Nachtrag 3 und Auftrag: Die Oberfläche ist ausschließlich Swing/Java2D im Comic-Stil. Kein Web-Frontend,
 * kein JavaFX, kein anderes UI-Toolkit im Produktionscode. Swing selbst bleibt auf app-swing und
 * comic-controls begrenzt ({@code ClassBoundaryTest.technologiesStayInTheirAdapter}).
 */
final class UiTechnologyRules {

    static final String[] FORBIDDEN_UI_PACKAGES = {
            "javafx..", "org.eclipse.swt..", "java.applet..",
            "javax.servlet..", "jakarta..", "javax.ws.rs..", "javax.faces..",
            "org.springframework..", "org.eclipse.jetty..", "io.undertow..", "org.apache.catalina..",
            "org.apache.tomcat..", "com.vaadin..", "org.thymeleaf..", "io.javalin..", "io.vertx..",
            "org.apache.wicket..", "com.google.gwt..", "org.teavm.."};

    private UiTechnologyRules() {
    }

    static ArchRule noWebFrontendOrOtherUiToolkit() {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_UI_PACKAGES)
                .because("die Oberfläche ist Swing/Java2D im Comic-Stil von askai-java8; kein Web-Frontend, "
                        + "kein JavaFX (Nachtrag 3)")
                .allowEmptyShould(true);
    }
}
