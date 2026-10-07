/**
 * Adapter: ACP über org.noear:acp-sdk (Strang G, AP17). Einziges Produktionsmodul mit ACP-SDK- und
 * Reactor-Typen; nach außen sichtbar sind nur die Typen aus {@code acp-client-api}.
 *
 * <p>Herkunft: {@code SolonAcpAgentConnector} und {@code SolonAcpRoundTripTest} aus Miguel0888/askai-java8,
 * Modul {@code acp-solon-client} (Stand eb07138). Angepasst: Paket, Systemeigenschaften des Tests,
 * zusätzliche Roundtrip-Fälle (Prozessende nach Shutdown, Absturz des Agenten, Startfehler).</p>
 */
package com.aresstack.enterpriseai.acp.solon;
