package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;

import java.util.Map;

/**
 * Wie der Agentenprozess seinen MCP-Endpoint erfährt: strukturiert über Umgebungsvariablen, nie als
 * Prompt-Text (Muster aus askai-java8 {@code AcpResearchSessionBackend}, dort mit {@code ASKAI_*}-Namen).
 *
 * <p>URL und Token sind Secret-Material (die URL trägt den Token im Pfad). {@code AgentLaunchSpec.toString()}
 * zeigt nur Variablennamen; diese Klasse loggt nichts.
 */
public final class AgentMcpEnvironment {

    public static final String ENDPOINT_ID = "ENTERPRISE_AI_MCP_ENDPOINT_ID";
    public static final String URL = "ENTERPRISE_AI_MCP_URL";
    public static final String TRANSPORT = "ENTERPRISE_AI_MCP_TRANSPORT";
    public static final String TOKEN = "ENTERPRISE_AI_MCP_TOKEN";

    private AgentMcpEnvironment() {
    }

    /** Trägt den Endpoint in {@code env} ein. Ein leerer Token wird weggelassen, nie als leerer Wert gesetzt. */
    public static void put(Map<String, String> env, AcpEndpointDescriptor endpoint) {
        env.put(ENDPOINT_ID, endpoint.getEndpointId());
        env.put(URL, endpoint.getUrl());
        env.put(TRANSPORT, endpoint.getTransport());
        String token = endpoint.getToken();
        if (token != null && !token.trim().isEmpty()) {
            env.put(TOKEN, token);
        }
    }

    /** Liest den Endpoint aus einer Umgebung oder {@code null}, wenn keiner übergeben wurde. */
    public static AcpEndpointDescriptor read(Map<String, String> env) {
        String url = env.get(URL);
        if (url == null || url.isEmpty()) {
            return null;
        }
        return new AcpEndpointDescriptor(env.get(ENDPOINT_ID), url, env.get(TRANSPORT), env.get(TOKEN));
    }
}
