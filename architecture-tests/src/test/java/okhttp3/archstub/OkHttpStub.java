package okhttp3.archstub;

/**
 * Stellvertreter für einen OkHttp-Typ (okhttp3..). Liegt nur auf dem Testklassenpfad von architecture-tests, damit
 * Gegenbeispiele gegen die Technologie- und Signaturregeln kompilieren, ohne dass die echte
 * Bibliothek hier eingebunden wird. Das Paket entspricht dem der echten Bibliothek, weil die Regeln
 * über Paketmuster arbeiten.
 */
public class OkHttpStub {

    public String describe() {
        return "einen OkHttp-Typ (okhttp3..)";
    }
}
