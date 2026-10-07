package com.aresstack.enterpriseai.architecture;

/**
 * Rolle eines Moduls in der Ports-and-Adapters-Struktur.
 */
enum ModuleKind {

    /** Reine Fachobjekte; kennt nur das JDK. */
    DOMAIN,

    /** Use Cases und Orchestrierung; kennt domain und Ports. */
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

    /** Dieses Modul selbst; enthält keine Produktionsklassen. */
    ARCHITECTURE_TESTS;

    /** Kernmodule dürfen weder Infrastrukturbibliotheken noch Swing/AWT kennen. */
    boolean isCore() {
        return this == DOMAIN || this == APPLICATION || this == PORT;
    }
}
