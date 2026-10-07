package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.mcp.KnowledgeToolSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent-Modus (ACP): Kommando des Agentenprozesses und Name des MCP-Endpoints, den jeder Agentenprozess mit
 * frischem Token bekommt. Deaktiviert bleibt die Shell im Chat-Modus; der Chat braucht den Agenten nicht.
 */
public final class AgentConfig {

    private final boolean enabled;
    private final String command;
    private final List<String> args;
    private final Duration requestTimeout;
    private final String mcpEndpointId;
    private final String mcpDisplayName;
    private final KnowledgeToolSettings toolSettings;

    AgentConfig(boolean enabled, String command, List<String> args, Duration requestTimeout, String mcpEndpointId,
                String mcpDisplayName, KnowledgeToolSettings toolSettings) {
        this.enabled = enabled;
        this.command = command;
        this.args = Collections.unmodifiableList(new ArrayList<String>(args));
        this.requestTimeout = requestTimeout;
        this.mcpEndpointId = mcpEndpointId;
        this.mcpDisplayName = mcpDisplayName;
        this.toolSettings = toolSettings == null ? KnowledgeToolSettings.defaults() : toolSettings;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Programm des Agenten, z. B. {@code java}; {@code null}, wenn der Agent-Modus deaktiviert ist. */
    public String command() {
        return command;
    }

    public List<String> args() {
        return args;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public String mcpEndpointId() {
        return mcpEndpointId;
    }

    public String mcpDisplayName() {
        return mcpDisplayName;
    }

    /** Grenzen der MCP-Wissenswerkzeuge (AP20), die der Agent an seinem Endpoint sieht. */
    public KnowledgeToolSettings toolSettings() {
        return toolSettings;
    }

    @Override
    public String toString() {
        return "AgentConfig[enabled=" + enabled + (enabled ? ", command=" + command + ", args=" + args.size()
                + ", requestTimeout=" + requestTimeout + ", mcpEndpointId=" + mcpEndpointId + ", tools=" + toolSettings : "")
                + "]";
    }
}
