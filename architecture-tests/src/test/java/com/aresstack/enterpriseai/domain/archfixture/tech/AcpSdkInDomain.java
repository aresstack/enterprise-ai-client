package com.aresstack.enterpriseai.domain.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;

/** Absichtlicher Verstoß: domain kennt das ACP SDK. */
public final class AcpSdkInDomain {

    private final AcpSdkStub acpSdk = new AcpSdkStub();

    public String describe() {
        return acpSdk.describe();
    }
}
