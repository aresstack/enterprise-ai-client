package com.aresstack.enterpriseai.acp.research;

import java.util.Map;

/**
 * Was der Host dem Agentenprozess über die Umgebung mitgibt (askai-java8 arch: {@code ResearchAgentEnvironment}):
 * den Research-Control-Endpoint als {@code ENTERPRISE_AI_MCP_*}-Variablen (Namen wie {@code AgentMcpEnvironment}
 * im Client). Die URL trägt den Token im Pfad; {@link #toString()} zeigt sie nie.
 */
final class ResearchAgentEnvironment {

    static final String ENDPOINT_ID = "ENTERPRISE_AI_MCP_ENDPOINT_ID";
    static final String URL = "ENTERPRISE_AI_MCP_URL";
    static final String TRANSPORT = "ENTERPRISE_AI_MCP_TRANSPORT";

    private final String endpointId;
    private final String researchUrl;
    private final String researchTransport;

    private ResearchAgentEnvironment(String endpointId, String researchUrl, String researchTransport) {
        this.endpointId = endpointId == null ? "" : endpointId;
        this.researchUrl = researchUrl == null ? "" : researchUrl;
        this.researchTransport = researchTransport == null ? "" : researchTransport;
    }

    static ResearchAgentEnvironment from(Map<String, String> env) {
        return new ResearchAgentEnvironment(env.get(ENDPOINT_ID), env.get(URL), env.get(TRANSPORT));
    }

    boolean hasResearchEndpoint() {
        return !researchUrl.isEmpty();
    }

    String researchUrl() {
        return researchUrl;
    }

    String researchTransport() {
        return researchTransport;
    }

    @Override
    public String toString() {
        return "ResearchAgentEnvironment[endpoint=" + endpointId + ", transport=" + researchTransport
                + ", url=" + (researchUrl.isEmpty() ? "-" : "<set>") + "]";
    }
}
