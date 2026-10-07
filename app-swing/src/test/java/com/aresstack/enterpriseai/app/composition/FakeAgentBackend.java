package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpServerRegistry;

import java.util.Collections;

/** Agent-Ports ohne Prozess: merkt sich die Startumgebung, Endpoint über die In-Process-Registry. */
final class FakeAgentBackend {

    final InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();
    final McpEndpointDefinition endpoint = new McpEndpointDefinition("agent-tools", "Wissenswerkzeuge");
    volatile AgentLaunchSpec lastSpec;

    AgentBackend backend() {
        return new AgentBackend(new AcpAgentConnector() {
            @Override
            public AcpConnection connect(AgentLaunchSpec spec) {
                lastSpec = spec;
                return new AcpConnection() {
                    private boolean closed;

                    @Override
                    public AcpConnectionState getState() {
                        return closed ? AcpConnectionState.CLOSED : AcpConnectionState.READY;
                    }

                    @Override
                    public AgentProcessHandle getProcess() {
                        return null;
                    }

                    @Override
                    public AcpSession newSession() {
                        return null;
                    }

                    @Override
                    public void close() {
                        closed = true;
                    }
                };
            }
        }, new AgentLaunchSpec("fake-agent", Collections.<String>emptyList(), null), registry, endpoint);
    }
}
