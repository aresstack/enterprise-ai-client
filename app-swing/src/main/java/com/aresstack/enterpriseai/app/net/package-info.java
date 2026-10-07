/**
 * Netzwerkregeln der Desktop-Anwendung (AP23): Proxy-Auswahl aus der Konfiguration, prozessweit über den
 * JVM-{@code ProxySelector} (so erreichen MediaWiki- und Chat-Adapter den Proxy ohne eigene Schnittstelle)
 * und als expliziter {@code Proxy} für Adapter, die ihn im Konstruktor erwarten (Embedding, Confluence).
 *
 * <p>Herkunft: askai-java8 {@code ProxyConfiguration} (Modi System/keiner/manuell), MainframeMate
 * {@code Settings} (Proxy-Host/-Port, Ausnahmen).
 */
package com.aresstack.enterpriseai.app.net;
