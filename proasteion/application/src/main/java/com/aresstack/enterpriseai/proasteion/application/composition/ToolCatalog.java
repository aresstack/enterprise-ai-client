package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.ApprovingMcpToolHandler;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Die zentrale Werkzeug-Registry der Anwendung: wer welche Werkzeuge beiträgt (Herkunft) und wo sie angeboten werden
 * (Kanäle). Chat- und Agentenpfad holen ihre Werkzeuge hier, der Einstellungen-Reiter „Tools“ zeigt
 * {@link #entries()}; eine zweite Liste gibt es nicht.
 *
 * <p>Name, Beschreibung, Schema und Handler bleiben in der {@link McpToolContribution}. Lieferanten werden bei jedem
 * Aufruf neu gefragt, damit phasenabhängige Sätze (Recherche-Agent) immer dem aktuellen Stand entsprechen. Werkzeuge,
 * die keine {@link McpToolContribution} sind (Anhang-Werkzeuge je Unterhaltung), stehen nur mit ihrer
 * {@link ToolDefinition} zur Anzeige im Katalog.
 *
 * <p>Aktiv oder abgeschaltet entscheidet allein {@code enabled} (produktiv {@code tools.disabled} der aktuellen
 * Einstellungen, bei jedem Aufruf frisch): {@link #tools}, {@link #find} und {@link #isEnabled} liefern nur aktive
 * Werkzeuge, {@link #entries()} alle mit ihrem Zustand. Ein abgeschaltetes Werkzeug bleibt registriert.
 *
 * <p>Pflichtwerkzeuge (bei der Registrierung benannt, z. B. {@code research_status} und {@code main_model_complete}
 * des Recherche-Agenten) sind nie abgeschaltet: ohne sie startet der Agent nicht. Ein Eintrag in
 * {@code tools.disabled} wird für sie ignoriert, der Reiter „Tools“ zeigt sie als nicht abwählbar.
 */
public final class ToolCatalog {

    private final List<Registration> registrations = new CopyOnWriteArrayList<Registration>();
    private final Predicate<String> enabled;
    private final ToolApprovalGate approvals;

    /** Alle Werkzeuge aktiv. */
    public ToolCatalog() {
        this(name -> true);
    }

    /** @param enabled sagt je Aufruf, ob das Werkzeug mit diesem Namen aktiv ist */
    public ToolCatalog(Predicate<String> enabled) {
        this(enabled, ToolApprovalGate.allowAll());
    }

    /**
     * @param enabled   sagt je Aufruf, ob das Werkzeug mit diesem Namen aktiv ist
     * @param approvals the binding approval check: every handler handed out by {@link #tools} and {@link #find} runs
     *                  only after it permits the call, for chat, agent and research paths alike
     */
    public ToolCatalog(Predicate<String> enabled, ToolApprovalGate approvals) {
        if (enabled == null || approvals == null) {
            throw new IllegalArgumentException("enabled and approvals must not be null");
        }
        this.enabled = enabled;
        this.approvals = approvals;
    }

    /** The approval check applied to every tool of this catalog. */
    public ToolApprovalGate approvals() {
        return approvals;
    }

    /**
     * Ist das Werkzeug aktiv (nicht abgeschaltet)? Unbekannte Namen und Pflichtwerkzeuge gelten als aktiv. Ein
     * Werkzeug, das nur die Mehrfachform eines anderen ist ({@code knowledge_read_many} zu {@code knowledge_read}),
     * ist mit diesem abgeschaltet: es liest dieselben Dokumente.
     */
    public boolean isEnabled(String name) {
        if (isRequired(name)) {
            return true;
        }
        if (!enabled.test(name)) {
            return false;
        }
        String single = KnowledgeMcpTools.KNOWLEDGE_READ_MANY.equals(name) ? KnowledgeMcpTools.KNOWLEDGE_READ : null;
        return single == null || isEnabled(single);
    }

    /** Ist das Werkzeug ein Pflichtwerkzeug einer Registrierung (nicht abschaltbar)? */
    public boolean isRequired(String name) {
        if (name == null) {
            return false;
        }
        for (Registration registration : registrations) {
            if (registration.required.test(name)) {
                return true;
            }
        }
        return false;
    }

    /** Registriert einen Werkzeugsatz; Reihenfolge der Registrierung = Reihenfolge der Werkzeuge. */
    public void register(String origin, Set<ToolChannel> channels,
                         final Supplier<? extends List<McpToolContribution>> tools) {
        register(origin, channels, tools, null);
    }

    /**
     * Wie oben; {@code required} benennt die Pflichtwerkzeuge dieses Satzes, die sich nicht abschalten lassen
     * ({@code null}: keine).
     */
    public void register(String origin, Set<ToolChannel> channels,
                         final Supplier<? extends List<McpToolContribution>> tools, Predicate<String> required) {
        if (tools == null) {
            throw new IllegalArgumentException("tools must not be null");
        }
        registrations.add(new Registration(origin, channels, tools, null, required));
    }

    /** Registriert Werkzeuge, die nur als {@link ToolDefinition} vorliegen (ausgeführt werden sie anderswo). */
    public void registerDefinitions(String origin, Set<ToolChannel> channels,
                                    Supplier<? extends List<ToolDefinition>> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        registrations.add(new Registration(origin, channels, null, definitions, null));
    }

    /** Die aktiven ausführbaren Werkzeuge, die in mindestens einem der Kanäle angeboten werden, ohne doppelte Namen. */
    public List<McpToolContribution> tools(Set<ToolChannel> anyOf) {
        List<McpToolContribution> result = new ArrayList<McpToolContribution>();
        Set<String> names = new HashSet<String>();
        for (Registration registration : registrations) {
            if (registration.tools == null || Collections.disjoint(registration.channels, anyOf)) {
                continue;
            }
            for (McpToolContribution tool : registration.tools.get()) {
                if (isEnabled(tool.getName()) && names.add(tool.getName())) {
                    result.add(gated(tool));
                }
            }
        }
        return result;
    }

    private McpToolContribution gated(McpToolContribution tool) {
        if (tool.getHandler() instanceof ApprovingMcpToolHandler) {
            return tool;
        }
        return new McpToolContribution(tool.getName(), tool.getDescription(), tool.getParameters(),
                new ApprovingMcpToolHandler(approvals, tool.getHandler()));
    }

    /** Das aktive ausführbare Werkzeug mit diesem Namen im Kanal oder {@code null}. */
    public McpToolContribution find(String name, ToolChannel channel) {
        for (McpToolContribution tool : tools(EnumSet.of(channel))) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * Alle Werkzeuge zur Anzeige, aktive wie abgeschaltete, in Registrierungsreihenfolge. Ein Name, der in mehreren Registrierungen steht,
     * erscheint einmal mit allen Kanälen; Herkunft und Beschreibung stammen aus der ersten.
     */
    public List<ToolEntry> entries() {
        List<String> order = new ArrayList<String>();
        Map<String, String> descriptions = new HashMap<String, String>();
        Map<String, String> origins = new HashMap<String, String>();
        Map<String, Set<ToolChannel>> channels = new HashMap<String, Set<ToolChannel>>();
        for (Registration registration : registrations) {
            List<String[]> described = new ArrayList<String[]>();
            if (registration.tools != null) {
                for (McpToolContribution tool : registration.tools.get()) {
                    described.add(new String[]{tool.getName(), tool.getDescription()});
                }
            } else {
                for (ToolDefinition definition : registration.definitions.get()) {
                    described.add(new String[]{definition.name(), definition.description()});
                }
            }
            for (String[] tool : described) {
                if (!channels.containsKey(tool[0])) {
                    order.add(tool[0]);
                    descriptions.put(tool[0], tool[1]);
                    origins.put(tool[0], registration.origin);
                    channels.put(tool[0], EnumSet.noneOf(ToolChannel.class));
                }
                channels.get(tool[0]).addAll(registration.channels);
            }
        }
        List<ToolEntry> entries = new ArrayList<ToolEntry>(order.size());
        for (String name : order) {
            boolean required = isRequired(name);
            entries.add(new ToolEntry(name, descriptions.get(name), origins.get(name), channels.get(name),
                    required || enabled.test(name), required));
        }
        return entries;
    }

    private static final class Registration {

        final String origin;
        final Set<ToolChannel> channels;
        final Supplier<? extends List<McpToolContribution>> tools;
        final Supplier<? extends List<ToolDefinition>> definitions;
        final Predicate<String> required;

        Registration(String origin, Set<ToolChannel> channels, Supplier<? extends List<McpToolContribution>> tools,
                     Supplier<? extends List<ToolDefinition>> definitions, Predicate<String> required) {
            if (origin == null || origin.trim().isEmpty() || channels == null || channels.isEmpty()) {
                throw new IllegalArgumentException("origin and channels must not be empty");
            }
            this.origin = origin.trim();
            this.channels = Collections.unmodifiableSet(EnumSet.copyOf(channels));
            this.tools = tools;
            this.definitions = definitions;
            this.required = required == null ? name -> false : required;
        }
    }
}
