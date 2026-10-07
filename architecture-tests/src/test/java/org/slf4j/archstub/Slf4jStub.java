package org.slf4j.archstub;

/**
 * Stellvertreter für einen SLF4J-Typ (org.slf4j..). Liegt nur auf dem Testklassenpfad von architecture-tests, damit
 * Gegenbeispiele gegen die Technologie- und Signaturregeln kompilieren, ohne dass die echte
 * Bibliothek hier eingebunden wird. Das Paket entspricht dem der echten Bibliothek, weil die Regeln
 * über Paketmuster arbeiten.
 */
public class Slf4jStub {

    public String describe() {
        return "einen SLF4J-Typ (org.slf4j..)";
    }
}
