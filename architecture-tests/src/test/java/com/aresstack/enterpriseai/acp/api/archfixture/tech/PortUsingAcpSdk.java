package com.aresstack.enterpriseai.acp.api.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;

/** Absichtlicher Verstoß (Nachtrag 4): acp-client-api kennt das ACP SDK. */
public final class PortUsingAcpSdk {

    private final AcpSdkStub acpSdk = new AcpSdkStub();

    public String describe() {
        return acpSdk.describe();
    }
}
