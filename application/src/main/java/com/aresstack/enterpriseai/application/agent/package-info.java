/**
 * Agent-Modus (AP21): der zusätzliche, optionale Modus neben dem normalen Chat. Ein externer Agent läuft als
 * eigener Prozess und wird über den neutralen ACP-Port ({@code acp-client-api}) angesprochen.
 *
 * <p>{@link com.aresstack.enterpriseai.application.agent.AgentService} kapselt den Lebenszyklus (Agent
 * starten, Verbindung, Session, Prompt mit Streaming-Updates, Abbruch, Schließen) und hält das
 * Agent-Transkript getrennt von den Konversationen des {@code ChatService}. Der Chat-Pfad
 * (UI → ChatService → ChatCompletionPort) kennt diesen Modus nicht und funktioniert ohne ihn.
 *
 * <p>Wie der Agent gestartet wird und welche MCP-Tool-Endpunkte er bekommt, entscheidet die Composition Root
 * über {@link com.aresstack.enterpriseai.application.agent.AgentLauncher}. Endpoint-Tokens und Launch-Umgebung
 * laufen dadurch nie durch diesen Use Case. Keine ACP-SDK-, Solon-, MCP-SDK- oder Swing-Typen.
 */
package com.aresstack.enterpriseai.application.agent;
