/**
 * Netzschicht der Desktop-Anwendung: die Proxy-Route je Ziel ({@code HttpRoutes}, Port {@code HttpRoutePort}
 * über win-proxy-java, mit Cache, Frist und Diagnose), die TLS-Vertrauensregel ({@code TrustPolicy} über
 * win-trust-java plus CA-Datei, verzögert gebaut) und ihr Bündel für die Composition Root
 * ({@code NetworkServices}); nichts davon wird prozessweit gesetzt, die Adapter bekommen Route, Socket-Factory und
 * User-Agent je Verbindung. Dazu die Diagnose gescheiterter Verbindungen ({@code ConnectionDiagnosis}:
 * Ursachenkette und Hinweis für die Oberfläche, ohne Secrets).
 *
 * <p>Herkunft: askai-java8 (Modi, Test-URL, „Proxy auflösen“, HTTP-Optionen), MainframeMate {@code Settings}
 * (Ausnahmen) und die Bibliotheken com.aresstack:win-proxy-java 0.2.0 und com.aresstack:win-trust-java 0.1.0.
 */
package com.aresstack.enterpriseai.app.net;
