package com.aresstack.enterpriseai.acp.demo.archfixture.logging;

/** Gegenprobe: der Demo-Agent schreibt Logs auf STDERR. */
public final class DemoAgentWritingStderr {

    public void log(String line) {
        System.err.println(line);
    }
}
