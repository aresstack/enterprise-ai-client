package com.aresstack.enterpriseai.acp.solon.archfixture.tech;

import com.agentclientprotocol.archstub.AcpSdkStub;
import org.noear.solon.archstub.SolonStub;
import reactor.core.archstub.ReactorStub;

/** Gegenprobe: ACP SDK, Reactor und Solon in acp-solon-client sind erlaubt. */
public final class AcpSdkInsideItsAdapter {

    private final AcpSdkStub acpSdk = new AcpSdkStub();
    private final ReactorStub reactor = new ReactorStub();
    private final SolonStub solon = new SolonStub();

    public String describe() {
        return acpSdk.describe() + " " + reactor.describe() + " " + solon.describe();
    }
}
