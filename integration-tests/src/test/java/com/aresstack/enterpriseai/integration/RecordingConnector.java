package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;

import java.util.concurrent.atomic.AtomicReference;

/** Reicht an den echten Connector durch und merkt sich Startparameter und Verbindung (Muster aus AP21). */
final class RecordingConnector implements AcpAgentConnector {

    private final AcpAgentConnector delegate;
    final AtomicReference<AgentLaunchSpec> spec = new AtomicReference<AgentLaunchSpec>();
    final AtomicReference<AcpConnection> connection = new AtomicReference<AcpConnection>();

    RecordingConnector(AcpAgentConnector delegate) {
        this.delegate = delegate;
    }

    @Override
    public AcpConnection connect(AgentLaunchSpec launchSpec) throws AcpException {
        spec.set(launchSpec);
        AcpConnection opened = delegate.connect(launchSpec);
        connection.set(opened);
        return opened;
    }
}
