package reactor.core.archstub;

/**
 * Stellvertreter für einen Reactor-Typ (reactor..). Liegt nur auf dem Testklassenpfad von architecture-tests, damit
 * Gegenbeispiele gegen die Technologie- und Signaturregeln kompilieren, ohne dass die echte
 * Bibliothek hier eingebunden wird. Das Paket entspricht dem der echten Bibliothek, weil die Regeln
 * über Paketmuster arbeiten.
 */
public class ReactorStub {

    public String describe() {
        return "einen Reactor-Typ (reactor..)";
    }
}
