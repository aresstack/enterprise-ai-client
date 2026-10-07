package com.aresstack.enterpriseai.app;

/**
 * Einstiegspunkt der Desktop-Anwendung und einzige Composition Root des Projekts.
 *
 * <p>Hier und nur hier werden später (AP23) Konfiguration gelesen, Adapter per Konstruktor erzeugt und
 * an die Application-Use-Cases sowie die Swing-Oberfläche übergeben. Kein anderes Modul darf Adapter
 * instanziieren oder globale Einstellungen laden. Derzeit noch ein Stub, der nichts zusammensetzt.
 */
public final class EnterpriseAiClientMain {

    private EnterpriseAiClientMain() {
    }

    public static void main(String[] args) {
        // Composition Root folgt in AP23; AP4 liefert die Swing-Shell.
    }
}
