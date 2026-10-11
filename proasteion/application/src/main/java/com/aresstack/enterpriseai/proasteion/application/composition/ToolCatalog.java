package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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
 */
public final class ToolCatalog {

    private final List<Registration> registrations = new CopyOnWriteArrayList<Registration>();

    /** Registriert einen Werkzeugsatz; Reihenfolge der Registrierung = Reihenfolge der Werkzeuge. */
    public void register(String origin, Set<ToolChannel> channels,
                         final Supplier<? extends List<McpToolContribution>> tools) {
        if (tools == null) {
            throw new IllegalArgumentException("tools must not be null");
        }
        registrations.add(new Registration(origin, channels, tools, null));
    }

    /** Registriert Werkzeuge, die nur als {@link ToolDefinition} vorliegen (ausgeführt werden sie anderswo). */
    public void registerDefinitions(String origin, Set<ToolChannel> channels,
                                    Supplier<? extends List<ToolDefinition>> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        registrations.add(new Registration(origin, channels, null, definitions));
    }

    /** Die ausführbaren Werkzeuge, die in mindestens einem der Kanäle angeboten werden, ohne doppelte Namen. */
    public List<McpToolContribution> tools(Set<ToolChannel> anyOf) {
        List<McpToolContribution> result = new ArrayList<McpToolContribution>();
        Set<String> names = new HashSet<String>();
        for (Registration registration : registrations) {
            if (registration.tools == null || Collections.disjoint(registration.channels, anyOf)) {
                continue;
            }
            for (McpToolContribution tool : registration.tools.get()) {
                if (names.add(tool.getName())) {
                    result.add(tool);
                }
            }
        }
        return result;
    }

    /** Das ausführbare Werkzeug mit diesem Namen im Kanal oder {@code null}. */
    public McpToolContribution find(String name, ToolChannel channel) {
        for (McpToolContribution tool : tools(EnumSet.of(channel))) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * Alle Werkzeuge zur Anzeige, in Registrierungsreihenfolge. Ein Name, der in mehreren Registrierungen steht,
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
            entries.add(new ToolEntry(name, descriptions.get(name), origins.get(name), channels.get(name)));
        }
        return entries;
    }

    private static final class Registration {

        final String origin;
        final Set<ToolChannel> channels;
        final Supplier<? extends List<McpToolContribution>> tools;
        final Supplier<? extends List<ToolDefinition>> definitions;

        Registration(String origin, Set<ToolChannel> channels, Supplier<? extends List<McpToolContribution>> tools,
                     Supplier<? extends List<ToolDefinition>> definitions) {
            if (origin == null || origin.trim().isEmpty() || channels == null || channels.isEmpty()) {
                throw new IllegalArgumentException("origin and channels must not be empty");
            }
            this.origin = origin.trim();
            this.channels = Collections.unmodifiableSet(EnumSet.copyOf(channels));
            this.tools = tools;
            this.definitions = definitions;
        }
    }
}
