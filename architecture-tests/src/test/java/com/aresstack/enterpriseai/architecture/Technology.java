package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;

/**
 * Infrastrukturtechnologien, die AP24 aus dem Kern fernhält bzw. auf genau ein Modul begrenzt.
 */
enum Technology {

    SWING("Swing/AWT", libraries("javax.swing..", "java.awt..")),
    HTTP("HTTP", libraries("java.net.http..", "okhttp3..", "org.apache.http..", "org.apache.hc..",
            "com.sun.net.httpserver..")),
    LUCENE("Lucene", libraries("org.apache.lucene.."), "knowledge.lucene"),
    SOLON("Solon", libraries("org.noear.solon..")),
    ACP_SDK("ACP SDK", libraries("com.agentclientprotocol..", "reactor.."), "acp.solon"),
    MCP_SDK("MCP SDK", libraries("org.noear.solon.ai.mcp..", "io.modelcontextprotocol.."), "mcp.solon"),
    KEEPASS("KeePass/KeePassRPC", libraries("org.java_websocket.."), "security.keepassrpc"),
    JWBF("JWBF/MediaWiki", libraries("net.sourceforge.jwbf.."), "source.mediawiki"),
    CONFLUENCE("Confluence", libraries(), "source.confluence");

    private final String label;
    private final String[] libraryPackages;
    private final String[] adapterPackages;

    Technology(String label, String[] libraryPackages, String... adapterPackageSuffixes) {
        this.label = label;
        this.libraryPackages = libraryPackages;
        this.adapterPackages = new String[adapterPackageSuffixes.length];
        for (int i = 0; i < adapterPackageSuffixes.length; i++) {
            this.adapterPackages[i] = ModuleRegistry.ROOT_PACKAGE + "." + adapterPackageSuffixes[i] + "..";
        }
    }

    private static String[] libraries(String... packages) {
        return packages;
    }

    String label() {
        return label;
    }

    /** Präfixe der Gradle-Koordinaten {@code group:name}, an denen die Bibliothek im Build erkannt wird. */
    String[] gradleCoordinatePrefixes() {
        switch (this) {
            case LUCENE:
                return new String[] {"org.apache.lucene:"};
            case SOLON:
                return new String[] {"org.noear:solon"};
            case ACP_SDK:
                return new String[] {"org.noear:acp-sdk", "io.projectreactor:"};
            case MCP_SDK:
                return new String[] {"org.noear:solon-ai-mcp", "io.modelcontextprotocol"};
            case KEEPASS:
                return new String[] {"org.java-websocket:"};
            case JWBF:
                return new String[] {"net.sourceforge:jwbf"};
            default:
                return new String[0];
        }
    }

    boolean matchesGradleCoordinate(String coordinate) {
        for (String prefix : gradleCoordinatePrefixes()) {
            if (coordinate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    boolean hasLibrary() {
        return libraryPackages.length > 0;
    }

    /** Technologie inklusive des eigenen Adaptermoduls – für Regeln, die den Kern schützen. */
    DescribedPredicate<JavaClass> predicate() {
        String[] all = new String[libraryPackages.length + adapterPackages.length];
        System.arraycopy(libraryPackages, 0, all, 0, libraryPackages.length);
        System.arraycopy(adapterPackages, 0, all, libraryPackages.length, adapterPackages.length);
        return withJdkHttp(resideInAnyPackage(all));
    }

    /** Nur die Fremdbibliothek – für Begrenzungsregeln; die Composition Root darf den Adapter instanziieren. */
    DescribedPredicate<JavaClass> libraryPredicate() {
        return withJdkHttp(resideInAnyPackage(libraryPackages));
    }

    private DescribedPredicate<JavaClass> withJdkHttp(DescribedPredicate<JavaClass> predicate) {
        if (this == HTTP) {
            // HttpURLConnection & Co. liegen in java.net, das für URI im Kern erlaubt bleibt.
            predicate = predicate.or(assignableTo(java.net.URLConnection.class));
        }
        return predicate.as(label);
    }
}
