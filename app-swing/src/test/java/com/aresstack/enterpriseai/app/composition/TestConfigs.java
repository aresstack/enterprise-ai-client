package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;

import java.nio.file.Path;
import java.util.Properties;

/** Testkonfigurationen ohne Netz, ohne Secrets. */
final class TestConfigs {

    private TestConfigs() {
    }

    static Properties base(Path indexDirectory) {
        Properties p = new Properties();
        p.setProperty("ui.windowTitle", "Enterprise AI Client (Test)");
        p.setProperty("chat.baseUrl", "http://127.0.0.1:9/v1");
        p.setProperty("chat.model", "test-chat");
        p.setProperty("chat.apiKeyRef", "Enterprise AI API");
        p.setProperty("chat.systemPrompt", "Antworte kurz.");
        p.setProperty("embedding.model", "test-embedding");
        p.setProperty("embedding.dimension", "8");
        p.setProperty("knowledge.indexDirectory", indexDirectory.toString());
        p.setProperty("knowledge.indexOnStartup", "true");
        p.setProperty("retrieval.maxResults", "5");
        p.setProperty("security.keepass.enabled", "false");
        p.setProperty("network.proxy.mode", "NONE");
        return p;
    }

    static AppConfig withoutAgent(Path indexDirectory) {
        return AppConfigLoader.fromProperties(base(indexDirectory));
    }

    static AppConfig withAgent(Path indexDirectory) {
        Properties p = base(indexDirectory);
        p.setProperty("agent.enabled", "true");
        p.setProperty("agent.command", "fake-agent");
        p.setProperty("agent.mcpEndpointId", "agent-tools");
        p.setProperty("agent.tools.defaultMaxResults", "3");
        return AppConfigLoader.fromProperties(p);
    }
}
