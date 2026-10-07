/**
 * Neutrale ACP-Client-Verträge (Strang G, AP16). Keine ACP-SDK-, Reactor-, Solon- oder Swing-Typen.
 *
 * <p>Die vier Lebenszyklen sind getrennt modelliert:</p>
 * <ul>
 *   <li>Prozess: {@link com.aresstack.enterpriseai.acp.api.AgentProcessHandle}, gestartet nach
 *       {@link com.aresstack.enterpriseai.acp.api.AgentLaunchSpec};</li>
 *   <li>Verbindung: {@link com.aresstack.enterpriseai.acp.api.AcpConnection} mit
 *       {@link com.aresstack.enterpriseai.acp.api.AcpConnectionState}, aufgebaut über den Port
 *       {@link com.aresstack.enterpriseai.acp.api.AcpAgentConnector};</li>
 *   <li>Session: {@link com.aresstack.enterpriseai.acp.api.AcpSession} mit
 *       {@link com.aresstack.enterpriseai.acp.api.AcpSessionState}, mehrere Prompts nacheinander;</li>
 *   <li>Prompt: {@link com.aresstack.enterpriseai.acp.api.PromptHandle} mit
 *       {@link com.aresstack.enterpriseai.acp.api.AcpPromptState}; Updates über
 *       {@link com.aresstack.enterpriseai.acp.api.AcpUpdateListener}, genau ein Terminal.</li>
 * </ul>
 *
 * <p>Zustandsübergänge werden zentral in {@link com.aresstack.enterpriseai.acp.api.AcpStates} geprüft;
 * {@link com.aresstack.enterpriseai.acp.api.PromptDispatcher} ist der wiederverwendbare Reihenfolge- und
 * Terminal-Wächter, den jeder Adapter für seine Prompt-Läufe nutzt.</p>
 *
 * <p>Herkunft: alle Typen und die Tests {@code AcpStatesTest}, {@code PromptDispatcherTest} stammen aus
 * Miguel0888/askai-java8, Modul {@code acp-client-api}, Paket {@code com.aresstack.askai.acp}
 * (Stand eb07138). Angepasst: Paket, {@code toString()} ohne Secrets in {@code AcpEndpointDescriptor}
 * und {@code AgentLaunchSpec}, Null-Prüfung des Listeners in {@code PromptDispatcher}.</p>
 */
package com.aresstack.enterpriseai.acp.api;
