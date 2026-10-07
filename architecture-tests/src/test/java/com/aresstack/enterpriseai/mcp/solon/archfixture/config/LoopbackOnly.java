package com.aresstack.enterpriseai.mcp.solon.archfixture.config;

/** Gegenprobe: Loopback-Adressen und ein Schema-Präfix ohne Host sind erlaubt. */
public final class LoopbackOnly {

    public String url(int port) {
        return "http://" + "127.0.0.1" + ":" + port + "/mcp";
    }

    public String local() {
        return "https://localhost/" + "http://127.0.0.1:8080/";
    }

    public String ipv6() {
        return "http://[::1]:8080/mcp";
    }
}
