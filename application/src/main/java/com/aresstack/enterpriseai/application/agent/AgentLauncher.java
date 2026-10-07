package com.aresstack.enterpriseai.application.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpException;

/**
 * Startet einen frischen Agentenprozess und liefert eine initialisierte ACP-Verbindung dazu.
 *
 * <p>Implementiert in der Composition Root: Sie kennt Kommando, Umgebung und die MCP-Endpunkte samt Token,
 * die der Agent bekommt. Der {@link AgentService} sieht davon nichts; er schließt nur die gelieferte
 * Verbindung, und das Schließen gibt auch alles frei, was der Launcher für diesen Prozess angelegt hat
 * (z. B. einen MCP-Endpoint, dessen Token damit ungültig wird).
 *
 * <p>Darf blockieren (Prozessstart, ACP-Initialisierung); der Service ruft ihn nie auf dem UI-Thread.
 */
public interface AgentLauncher {

    AcpConnection launch() throws AcpException;
}
