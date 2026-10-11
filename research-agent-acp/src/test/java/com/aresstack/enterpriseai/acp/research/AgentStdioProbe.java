package com.aresstack.enterpriseai.acp.research;

import java.util.logging.Logger;

/** Kind-Prozess des Prozessgrenzen-Tests: stellt die Ströme wie der Agent um und schreibt JSON und eine Logzeile. */
public final class AgentStdioProbe {

    private AgentStdioProbe() {
    }

    public static void main(String[] args) throws Exception {
        AgentStdio.install();
        // Eingang wie StdioAcpAgentTransport: STDIN als UTF-8 (der Client schreibt UTF-8-Bytes).
        String input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in,
                java.nio.charset.StandardCharsets.UTF_8)).readLine();
        Logger.getLogger(AgentStdioProbe.class.getName()).info("Log: " + AgentStdioBoundaryTest.TEXT);
        System.out.println("{\"text\":\"" + AgentStdioBoundaryTest.TEXT + "\",\"echo\":\"" + input + "\"}");
        System.out.flush();
    }
}
