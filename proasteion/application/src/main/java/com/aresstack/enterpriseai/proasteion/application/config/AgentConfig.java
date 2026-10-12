package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeToolSettings;

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
    private final int researchMaxPages;

    AgentConfig(boolean enabled, String command, List<String> args, Duration requestTimeout, String mcpEndpointId,
                String mcpDisplayName, KnowledgeToolSettings toolSettings) {
        this(enabled, command, args, requestTimeout, mcpEndpointId, mcpDisplayName, toolSettings,
                DEFAULT_RESEARCH_MAX_PAGES);
    }

    AgentConfig(boolean enabled, String command, List<String> args, Duration requestTimeout, String mcpEndpointId,
                String mcpDisplayName, KnowledgeToolSettings toolSettings, int researchMaxPages) {
        this.researchMaxPages = researchMaxPages;
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

    /**
     * {@code agent.research.maxPages}: wie in arch („Seiten“, maximal besuchte Seiten pro Suchlauf) zählt das
     * Recherche-Budget die Seiten, die der Agent tatsächlich öffnet ({@code knowledge_read}); jede Suche
     * ({@code knowledge_search}) zählt als Werkzeugaufruf. Vorladen im Hintergrund verbraucht nie Budget.
     * {@code 0} = unbegrenzt.
     */
    public int researchMaxPages() {
        return researchMaxPages;
    }

    /** Standard des Recherche-Seitenbudgets. */
    public static final int DEFAULT_RESEARCH_MAX_PAGES = 4;

    @Override
    public String toString() {
        return "AgentConfig[enabled=" + enabled + (enabled ? ", command=" + command + ", args=" + args.size()
                + ", requestTimeout=" + requestTimeout + ", mcpEndpointId=" + mcpEndpointId + ", tools=" + toolSettings : "")
                + ", researchMaxPages=" + researchMaxPages + "]";
    }
}
