package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
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
    private final ApprovalRules toolApprovals;
    private final BubbleColorsConfig bubbleColors;
    private final FactStoreConfig facts;
    private final TextMiningConfig textMining;
    private final FileAccessConfig fileAccess;
    private final BrowserAccessConfig browserAccess;
    private final List<String> warnings;

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors,
              List<String> warnings) {
        this(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models, disabledTools,
                bubbleColors, FactStoreConfig.defaults(), warnings);
    }

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors, FactStoreConfig facts,
              List<String> warnings) {
        this(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models, disabledTools,
                bubbleColors, facts, TextMiningConfig.defaults(), warnings);
    }

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors, FactStoreConfig facts,
              TextMiningConfig textMining, List<String> warnings) {
        this(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models, disabledTools,
                bubbleColors, facts, textMining, FileAccessConfig.defaults(), warnings);
    }

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors, FactStoreConfig facts,
              TextMiningConfig textMining, FileAccessConfig fileAccess, List<String> warnings) {
        this(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models, disabledTools,
                bubbleColors, facts, textMining, fileAccess, ApprovalRules.none(), warnings);
    }

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors, FactStoreConfig facts,
              TextMiningConfig textMining, FileAccessConfig fileAccess, ApprovalRules toolApprovals,
              List<String> warnings) {
        this(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models, disabledTools,
                bubbleColors, facts, textMining, fileAccess, toolApprovals, BrowserAccessConfig.defaults(), warnings);
    }

    AppConfig(String windowTitle, ChatConfig chat, EmbeddingConfig embedding, KnowledgeConfig knowledge,
              List<SourceDefinition> sources, KeePassConfig keePass, NetworkConfig network, AgentConfig agent,
              ModelsConfig models, Set<String> disabledTools, BubbleColorsConfig bubbleColors, FactStoreConfig facts,
              TextMiningConfig textMining, FileAccessConfig fileAccess, ApprovalRules toolApprovals,
              BrowserAccessConfig browserAccess, List<String> warnings) {
        this.browserAccess = browserAccess == null ? BrowserAccessConfig.defaults() : browserAccess;
        this.toolApprovals = toolApprovals == null ? ApprovalRules.none() : toolApprovals;
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
        this.bubbleColors = bubbleColors == null ? BubbleColorsConfig.defaults() : bubbleColors;
        this.facts = facts == null ? FactStoreConfig.defaults() : facts;
        this.textMining = textMining == null ? TextMiningConfig.defaults() : textMining;
        this.fileAccess = fileAccess == null ? FileAccessConfig.defaults() : fileAccess;
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

    /** {@code tools.approval}: approval rules per tool, action and scope; without a rule a tool is allowed. */
    public ApprovalRules toolApprovals() {
        return toolApprovals;
    }

    /** Farben der Sprechblasen von Nutzer und Assistent ({@code chat.bubble.*}); ohne Schlüssel die Vorgaben. */
    public BubbleColorsConfig bubbleColors() {
        return bubbleColors;
    }

    /** The global metadata catalog ({@code facts.*}). */
    public FactStoreConfig facts() {
        return facts;
    }

    /** Text mining in the reader ({@code textMining.*}, {@code reader.acronyms.*}). */
    public TextMiningConfig textMining() {
        return textMining;
    }

    /** Granted folders for local files and network drives ({@code files.access.*}). */
    public FileAccessConfig fileAccess() {
        return fileAccess;
    }

    /** Global access policy of the chat browsers ({@code browser.access.*}). */
    public BrowserAccessConfig browserAccess() {
        return browserAccess;
    }

    public List<String> warnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "AppConfig[" + chat + ", " + embedding + ", " + knowledge + ", sources=" + sources + ", " + keePass
                + ", " + network + ", " + agent + ", " + models + ", " + facts + ", " + textMining + ", " + fileAccess + ", "
                + browserAccess + "]";
    }
}
