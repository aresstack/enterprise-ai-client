package com.aresstack.enterpriseai.mcp.solon.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;

/** Absichtlicher Verstoß: ACP SDK außerhalb von acp-solon-client/acp-demo-agent. */
public final class AcpSdkInMcpRuntime {

    private final AcpSdkStub acpSdk = new AcpSdkStub();

    public String describe() {
        return acpSdk.describe();
    }
}
