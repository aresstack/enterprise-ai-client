package org.jsoup.archstub;

/**
 * Stellvertreter für einen jsoup-Typ (org.jsoup..). Liegt nur auf dem Testklassenpfad von architecture-tests, damit
 * Gegenbeispiele gegen die Technologie- und Signaturregeln kompilieren, ohne dass die echte
 * Bibliothek hier eingebunden wird. Das Paket entspricht dem der echten Bibliothek, weil die Regeln
 * über Paketmuster arbeiten.
 */
public class JsoupStub {

    public String describe() {
        return "einen jsoup-Typ (org.jsoup..)";
    }
}
