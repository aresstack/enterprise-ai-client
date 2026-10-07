package com.aresstack.enterpriseai.acp.demo.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;
import reactor.core.archstub.ReactorStub;

/** Gegenprobe: Reactor und ACP SDK im Demo-Agenten sind erlaubt. */
public final class ReactorInsideDemoAgent {

    private final ReactorStub reactor = new ReactorStub();
    private final AcpSdkStub acpSdk = new AcpSdkStub();

    public String describe() {
        return reactor.describe() + " " + acpSdk.describe();
    }
}
