package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Der Werkzeugsatz des Agenten-Endpoints folgt der Phase (askai arch {@code ResearchControlEndpoint.refreshTools}):
 * der Launcher übergibt nach dem Registrieren Registry und Handle, jeder Zustandswechsel des Hosts ruft
 * {@link #refresh()}, und {@code updateTools} meldet dem Agenten {@code tools/list_changed}. Sichtbarkeit ist
 * keine Erlaubnis: jedes Schreibwerkzeug prüft beim Aufruf erneut.
 */
public final class PhaseToolSetController {

    private static final Logger LOG = Logger.getLogger(PhaseToolSetController.class.getName());

    private final Supplier<List<McpToolContribution>> toolsForCurrentState;
    private McpServerRegistry registry;
    private McpEndpointHandle handle;

    /** @param toolsForCurrentState berechnet den Werkzeugsatz aus dem aktuellen Zustand (bei jedem Aufruf neu) */
    public PhaseToolSetController(Supplier<List<McpToolContribution>> toolsForCurrentState) {
        if (toolsForCurrentState == null) {
            throw new IllegalArgumentException("toolsForCurrentState must not be null");
        }
        this.toolsForCurrentState = toolsForCurrentState;
    }

    /** Der Werkzeugsatz des aktuellen Zustands. */
    public List<McpToolContribution> current() {
        return toolsForCurrentState.get();
    }

    /** Vom Launcher nach dem Registrieren: ab jetzt geht jeder Zustandswechsel an diesen Endpoint. */
    public synchronized void attach(McpServerRegistry registry, McpEndpointHandle handle) {
        this.registry = registry;
        this.handle = handle;
    }

    /** Vom Launcher beim Abmelden des Endpoints (nur, wenn es noch derselbe ist). */
    public synchronized void detach(McpEndpointHandle released) {
        if (handle != null && handle.equals(released)) {
            registry = null;
            handle = null;
        }
    }

    /** Berechnet den Werkzeugsatz neu und veröffentlicht ihn ({@code tools/list_changed}). */
    public void refresh() {
        McpServerRegistry currentRegistry;
        McpEndpointHandle currentHandle;
        synchronized (this) {
            currentRegistry = registry;
            currentHandle = handle;
        }
        if (currentRegistry == null || currentHandle == null) {
            return;
        }
        try {
            List<McpToolContribution> tools = current();
            currentRegistry.updateTools(currentHandle, tools);
            LOG.info("[research-tools] " + tools.size() + " Werkzeuge veröffentlicht");
        } catch (RuntimeException failed) {
            LOG.warning("[research-tools] Werkzeugsatz nicht veröffentlicht: " + failed.getMessage());
        }
    }
}
