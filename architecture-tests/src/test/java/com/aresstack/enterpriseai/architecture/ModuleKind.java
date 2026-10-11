package com.aresstack.enterpriseai.architecture;

/**
 * Rolle eines Moduls in der Ports-and-Adapters-Struktur.
 */
enum ModuleKind {

    /**
     * Grenze der Stadt aus corenth ohne eigene Klassen (proasteion, emporion, platform): hält nur die darin
     * geschachtelten Module zusammen.
     */
    BOUNDARY,
    /** Reine Fachobjekte; kennt nur das JDK. */
    DOMAIN,

    /** Use Cases und Orchestrierung; kennt astu und Ports. */
    APPLICATION,

    /** Neutrale Port-Verträge einer Fähigkeit ({@code *-api}). */
    PORT,

    /** Technische Implementierung genau eines Ports. */
    ADAPTER,

    /** Wiederverwendbare Swing/Java2D-Bibliothek ohne Projektabhängigkeiten. */
    UI_LIBRARY,

    /** Äußerstes Modul: Swing-Anwendung und einzige Composition Root. */
    COMPOSITION_ROOT,

    /** Separater Prozess bzw. Testfixture; darf von keinem Modul referenziert werden. */
    TEST_FIXTURE,

    /**
     * Eigener Agentenprozess mit {@code main} (eingebauter Recherche-Agent über ACP): sieht Kern, Ports und
     * Adapter, die er selbst zusammensetzt; die Composition Root nimmt ihn nur zur Laufzeit ins Jar
     * ({@code runtimeOnly}) und startet ihn über den Klassennamen.
     */
    AGENT_PROCESS,

    /** Dieses Modul selbst; enthält keine Produktionsklassen. */
    ARCHITECTURE_TESTS,

    /**
     * Modulübergreifende Integrations- und Vertical-Slice-Tests (AP25): nur Testcode, der über den Testklassenpfad
     * alle Module und Testfixtures sieht; keine Produktionsabhängigkeiten, von keinem Modul referenziert.
     */
    INTEGRATION_TESTS;

    /** Kernmodule dürfen weder Infrastrukturbibliotheken noch Swing/AWT kennen. */
    boolean isCore() {
        return this == DOMAIN || this == APPLICATION || this == PORT;
    }
}
