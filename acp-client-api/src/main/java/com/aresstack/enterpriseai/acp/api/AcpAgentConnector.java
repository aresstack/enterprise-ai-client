package com.aresstack.enterpriseai.acp.api;

/** Spawns the external agent and establishes an initialized ACP connection. */
public interface AcpAgentConnector {

    AcpConnection connect(AgentLaunchSpec spec) throws AcpException;
}
