/**
 * Anbindung und Verdrahtung des Agent-Modus (AP21), wiederverwendbar für die Composition Root (AP23).
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.app.agent.AgentServiceBinding}: Bedienabsichten der Agent-Ansicht →
 *       {@code AgentService}, dessen Callbacks zurück ins eigene Presentation-Model (UI-Thread).</li>
 *   <li>{@link com.aresstack.enterpriseai.app.agent.AcpAgentLauncher}: startet den Agenten über den
 *       ACP-Port und gibt ihm optional einen eigenen MCP-Endpoint mit, über
 *       {@link com.aresstack.enterpriseai.app.agent.AgentMcpEnvironment} als Umgebungsvariablen.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.agent.AgentModeAssembly}: baut die Agent-Ansicht samt Anbindung.</li>
 * </ul>
 *
 * <p>Nur hier und in der Composition Root kommen ACP- und MCP-Typen in app-swing vor (geprüft in
 * {@code AgentModeBoundaryTest}); der Chat-Pfad ({@code app.chat}, {@code app.ui.chat}) bleibt frei davon.
 */
package com.aresstack.enterpriseai.app.agent;
