package com.aresstack.enterpriseai.astu.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;

/** Absichtlicher Verstoß: astu kennt das ACP SDK. */
public final class AcpSdkInDomain {

    private final AcpSdkStub acpSdk = new AcpSdkStub();

    public String describe() {
        return acpSdk.describe();
    }
}
