package com.aresstack.enterpriseai.acp.demo.archfixture.config;

/** Gegenprobe: der Demo-Agent darf seine Umgebung lesen. */
public final class DemoAgentReadingEnvironment {

    public String marker() {
        return System.getenv("MARKER");
    }
}
