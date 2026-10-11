package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.source.SourceDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Der vollständige, unveränderliche Konfigurations-Snapshot der Anwendung. Enthält keine Secrets, nur
 * {@link com.aresstack.enterpriseai.astu.security.SecretRef}-Verweise; {@link #toString()} ist deshalb loggbar.
 */
public final class AppConfig {

    private final String windowTitle;
    private final ChatConfig chat;
    private final EmbeddingConfig embedding;
    private final KnowledgeConfig knowledge;
    private final List<SourceDefinition> sources;
    private final KeePassConfig keePass;
    private final NetworkConfig network;
    private final AgentConfig agent;
    private final ModelsConfig models;
    private final Set<String> disabledTools;
    private final List<String> warnings;

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, List<String> warnings) {
        this.windowTitle = windowTitle;
        this.chat = chat;
        this.embedding = embedding;
        this.knowledge = knowledge;
        this.sources = Collections.unmodifiableList(new ArrayList<SourceDefinition>(sources));
        this.keePass = keePass;
        this.network = network;
        this.agent = agent;
        this.models = models;
        this.disabledTools = Collections.unmodifiableSet(new LinkedHashSet<String>(disabledTools));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public String windowTitle() {
        return windowTitle;
    }

    public ChatConfig chat() {
        return chat;
    }

    public EmbeddingConfig embedding() {
        return embedding;
    }

    public KnowledgeConfig knowledge() {
        return knowledge;
    }

    /** Wissensquellen in Konfigurationsreihenfolge, typneutral; Einstellungen prüft der Quellen-Port des Adapters. */
    public List<SourceDefinition> sources() {
        return sources;
    }

    public KeePassConfig keePass() {
        return keePass;
    }

    public NetworkConfig network() {
        return network;
    }

    public AgentConfig agent() {
        return agent;
    }

    /** Modellauswahl je Kategorie und Modellverzeichnis lokaler Stimmen. */
    public ModelsConfig models() {
        return models;
    }

    /** Hinweise aus dem Laden (unbekannte Schlüssel, fehlendes KeePass trotz SecretRefs); keine Fehler. */
    /** {@code tools.disabled}: die abgeschalteten Werkzeuge nach Namen; alle anderen (auch neue) sind aktiv. */
    public Set<String> disabledTools() {
        return disabledTools;
    }

    /** Ist das Werkzeug aktiv? Ohne Eintrag in {@code tools.disabled} ja. */
    public boolean isToolEnabled(String name) {
        return name != null && !disabledTools.contains(name);
    }

    public List<String> warnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "AppConfig[" + chat + ", " + embedding + ", " + knowledge + ", sources=" + sources + ", " + keePass
                + ", " + network + ", " + agent + ", " + models + "]";
    }
}
