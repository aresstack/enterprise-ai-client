/**
 * Anbindung des Agent-Modus (AP21) in der Schale; Start und Werkzeuge verdrahtet der Kompositionspunkt
 * ({@code proasteion.application.agent}).
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.proasteion.exedra.agent.AgentServiceBinding}: Bedienabsichten der Agent-Ansicht →
 *       {@code AgentService}, dessen Callbacks zurück ins eigene Presentation-Model (UI-Thread).</li>
 *   <li>{@link com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher}: startet den Agenten über den
 *       ACP-Port und gibt ihm optional einen eigenen MCP-Endpoint mit, über
 *       {@link com.aresstack.enterpriseai.proasteion.application.agent.AgentMcpEnvironment} als Umgebungsvariablen.</li>
 *   <li>{@link com.aresstack.enterpriseai.proasteion.exedra.agent.AgentModeAssembly}: baut die Agent-Ansicht samt Anbindung.</li>
 * </ul>
 *
 * <p>Nur hier kommen ACP-Typen in exedra vor (geprüft in
 * {@code AgentModeBoundaryTest}); der Chat-Pfad ({@code app.chat}, {@code app.ui.chat}) bleibt frei davon.
 */
package com.aresstack.enterpriseai.proasteion.exedra.agent;
