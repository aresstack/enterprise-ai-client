package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.config.AgentConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.InMemoryRawKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alle Ports, gegen die die Anwendung gebaut wird. Die Ports selbst bleiben dieselben Objekte; Ports, die
 * Einstellungen ohne Neustart übernehmen (Modelle, Index), melden sich als {@link Reconfigurable} und werden über
 * {@link #apply} auf den gespeicherten Stand gebracht. Produktiv liefert sie
 * {@link AdapterAssembly} mit echten Adaptern, der Kompositionstest mit Fakes; {@link CompositionRoot} kennt
 * den Unterschied nicht. {@link #close()} schließt die mitgegebenen Ressourcen in Reihenfolge (zuletzt der
 * Index) und ist idempotent.
 */
public final class ApplicationPorts implements Closeable {

    private static final Logger LOG = Logger.getLogger(ApplicationPorts.class.getName());

    private final ChatCompletionPort chat;
    private final EmbeddingPort embeddings;
    private final com.aresstack.enterpriseai.model.api.RerankPort reranker;
    private final Supplier<EmbeddingModelIdentity> embeddingSpace;
    private final KnowledgeIndexPort index;
    private final RawKnowledgeIndexPort rawIndex;
    private final FactStorePort facts;
    private final com.aresstack.enterpriseai.browser.api.ChatBrowserPort browsers;
    private final com.aresstack.enterpriseai.memory.api.MemoryStorePort memories;
    private final KnowledgeSourceCatalog sources;
    private final SecretProvider secrets;
    private final AgentBackend agent;
    private final Function<AgentConfig, AgentBackend> agents;
    private final AgentBackend researchAgent;
    private final List<KnowledgeSourceProvider> sourceProviders;
    private final List<String> sourceWarnings;
    private final List<NamedResource> resources;
    private final List<Reconfigurable> reconfigurables;
    private boolean closed;

    /** Ein Port, der gespeicherte Einstellungen ohne Neustart übernimmt. */
    interface Reconfigurable {
        void apply(AppConfig config, NetworkServices network);
    }

    private ApplicationPorts(Builder builder) {
        if (builder.chat == null || builder.embeddings == null || builder.embeddingSpace == null
                || builder.index == null || builder.secrets == null) {
            throw new IllegalArgumentException("chat, embeddings, embeddingSpace, index and secrets must be given");
        }
        this.chat = builder.chat;
        this.embeddings = builder.embeddings;
        this.reranker = builder.reranker;
        this.embeddingSpace = builder.embeddingSpace;
        this.index = builder.index;
        this.rawIndex = builder.rawIndex == null ? new InMemoryRawKnowledgeIndex() : builder.rawIndex;
        this.facts = builder.facts == null ? new UnavailableFactStore() : builder.facts;
        this.browsers = builder.browsers;
        this.memories = builder.memories;
        this.sources = builder.sources == null ? KnowledgeSourceCatalog.empty() : builder.sources;
        this.secrets = builder.secrets;
        this.agent = builder.agent;
        this.agents = builder.agents;
        this.researchAgent = builder.researchAgent;
        this.sourceProviders = Collections.unmodifiableList(new ArrayList<KnowledgeSourceProvider>(builder.sourceProviders));
        this.sourceWarnings = Collections.unmodifiableList(new ArrayList<String>(builder.sourceWarnings));
        this.resources = Collections.unmodifiableList(new ArrayList<NamedResource>(builder.resources));
        this.reconfigurables = Collections.unmodifiableList(new ArrayList<Reconfigurable>(builder.reconfigurables));
    }

    public static Builder builder() {
        return new Builder();
    }

    public ChatCompletionPort chat() {
        return chat;
    }

    public EmbeddingPort embeddings() {
        return embeddings;
    }

    /** The cross-encoder of the selected RERANK model ({@code model.rerank}); {@code null} = none. */
    public com.aresstack.enterpriseai.model.api.RerankPort reranker() {
        return reranker;
    }

    /** Die Embedding-Welt (Index-Namensraum) des aktuellen Stands. */
    public EmbeddingModelIdentity embeddingSpace() {
        return embeddingSpace.get();
    }

    /** Die Embedding-Welt als Funktion: Use Cases fragen sie je Aufruf, damit ein Modellwechsel sofort gilt. */
    public Supplier<EmbeddingModelIdentity> embeddingSpaceSource() {
        return embeddingSpace;
    }

    /**
     * Bringt die Ports, die Einstellungen ohne Neustart übernehmen, auf den gespeicherten Stand (Beobachter des
     * Settings-Service). Ein Port, der scheitert, behält seinen bisherigen Stand; die übrigen übernehmen trotzdem.
     */
    public void apply(AppConfig config, NetworkServices network) {
        if (config == null || network == null) {
            throw new IllegalArgumentException("config and network must not be null");
        }
        for (Reconfigurable reconfigurable : reconfigurables) {
            try {
                reconfigurable.apply(config, network);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Einstellungen nicht übernommen von " + reconfigurable, e);
            }
        }
    }

    public KnowledgeIndexPort index() {
        return index;
    }

    /**
     * The global metadata catalog (#106): facts bound to the stable resource ids, independent of the index. Calls
     * block on storage; never call it on the Swing event thread.
     */
    public FactStorePort facts() {
        return facts;
    }

    /** The browsers of the chats (#141), or {@code null} when no browser engine is available. */
    public com.aresstack.enterpriseai.browser.api.ChatBrowserPort browsers() {
        return browsers;
    }

    /** Persistent memories (#144), or {@code null} without a memory store: then the chat has no memory tools. */
    public com.aresstack.enterpriseai.memory.api.MemoryStorePort memories() {
        return memories;
    }

    /**
     * Rohindex der geladenen Seiten (sofort lexikalisch durchsuchbar, ohne Passagen und Embeddings); ohne eigenen
     * einer im Speicher.
     */
    public RawKnowledgeIndexPort rawIndex() {
        return rawIndex;
    }

    /**
     * Ein Quellen-Port je Quelltyp (MediaWiki, Confluence, lokale Dateien ...), mit denselben Secrets und derselben
     * Netzregel wie beim Start. Daraus bietet „+ Quelle“ die Typen an und bindet neue Quellen ohne Neustart an;
     * leer: Änderungen gelten erst nach dem nächsten Start.
     */
    public List<KnowledgeSourceProvider> sourceProviders() {
        return sourceProviders;
    }

    /** Hinweise zu Quellen, die beim Start übersprungen wurden (fehlerhafte Einstellungen, unbekannter Typ). */
    public List<String> sourceWarnings() {
        return sourceWarnings;
    }

    public KnowledgeSourceCatalog sources() {
        return sources;
    }

    public SecretProvider secrets() {
        return secrets;
    }

    /** Der Tresorzugang der Schale (Titel, Passwortverwaltung, {@code MM_CertAlias}) über diesem Security-Port. */
    public KeePassAccess keePassAccess() {
        return new KeePassAccess(secrets, null);
    }

    /** {@code null}: kein Agent-Modus konfiguriert. */
    public AgentBackend agent() {
        return agent;
    }

    public boolean hasAgent() {
        return agent != null;
    }

    /**
     * Das eigene Agent-Kommando nach den aktuellen Agent-Einstellungen, gebaut je Agent-Sitzung ({@code null}:
     * ausgeschaltet, der eingebaute Recherche-Agent übernimmt). Ohne Fabrik das Agent-Kommando des Starts.
     */
    /**
     * Der eingebaute Recherche-Agent als eigener Prozess über ACP (askai-java8 arch: {@code ResearchAgentMain}),
     * gestartet mit derselben Java-Laufzeit aus demselben Jar; {@code null}: nicht angebunden (Tests).
     */
    public AgentBackend researchAgent() {
        return researchAgent;
    }

    public AgentBackend agentFor(AgentConfig settings) {
        if (agents == null) {
            return agent;
        }
        return settings != null && settings.enabled() ? agents.apply(settings) : null;
    }

    /** Namen der Ressourcen in Schließreihenfolge (für Tests und Log). */
    public List<String> resourceNames() {
        List<String> names = new ArrayList<String>();
        for (NamedResource resource : resources) {
            names.add(resource.name);
        }
        return names;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (NamedResource resource : resources) {
            try {
                resource.closeable.close();
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Ressource " + resource.name + " nicht sauber geschlossen: "
                        + e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public String toString() {
        return "ApplicationPorts[chat=" + chat + ", embeddings=" + embeddings + ", index=" + index
                + ", sources=" + sources.ids() + ", secrets=" + secrets + ", agent=" + agent + "]";
    }

    private static final class NamedResource {
        final String name;
        final Closeable closeable;

        NamedResource(String name, Closeable closeable) {
            this.name = name;
            this.closeable = closeable;
        }
    }

    public static final class Builder {
        private ChatCompletionPort chat;
        private EmbeddingPort embeddings;
        private com.aresstack.enterpriseai.model.api.RerankPort reranker;
        private Supplier<EmbeddingModelIdentity> embeddingSpace;
        private KnowledgeIndexPort index;
        private RawKnowledgeIndexPort rawIndex;
        private FactStorePort facts;
        private com.aresstack.enterpriseai.browser.api.ChatBrowserPort browsers;
        private com.aresstack.enterpriseai.memory.api.MemoryStorePort memories;
        private KnowledgeSourceCatalog sources;
        private SecretProvider secrets;
        private AgentBackend agent;
        private Function<AgentConfig, AgentBackend> agents;
        private AgentBackend researchAgent;
        private final List<KnowledgeSourceProvider> sourceProviders = new ArrayList<KnowledgeSourceProvider>();
        private final List<String> sourceWarnings = new ArrayList<String>();
        private final List<NamedResource> resources = new ArrayList<NamedResource>();
        private final List<Reconfigurable> reconfigurables = new ArrayList<Reconfigurable>();

        private Builder() {
        }

        public Builder chat(ChatCompletionPort value) {
            this.chat = value;
            return this;
        }

        public Builder reranker(com.aresstack.enterpriseai.model.api.RerankPort value) {
            this.reranker = value;
            return this;
        }

        public Builder embeddings(EmbeddingPort value, final EmbeddingModelIdentity space) {
            this.embeddings = value;
            this.embeddingSpace = space == null ? null : new Supplier<EmbeddingModelIdentity>() {
                @Override
                public EmbeddingModelIdentity get() {
                    return space;
                }
            };
            return this;
        }

        /** Ein Embedding-Port, dessen Welt wechseln kann: der Index-Namensraum folgt seiner {@code modelIdentity()}. */
        public Builder liveEmbeddings(final EmbeddingPort value) {
            this.embeddings = value;
            this.embeddingSpace = value == null ? null : new Supplier<EmbeddingModelIdentity>() {
                @Override
                public EmbeddingModelIdentity get() {
                    return value.modelIdentity();
                }
            };
            return this;
        }

        /** Ein Port, der über {@link ApplicationPorts#apply} gespeicherte Einstellungen übernimmt. */
        Builder reconfiguring(Reconfigurable value) {
            if (value == null) {
                throw new IllegalArgumentException("reconfigurable must not be null");
            }
            this.reconfigurables.add(value);
            return this;
        }

        public Builder index(KnowledgeIndexPort value) {
            this.index = value;
            return this;
        }

        public Builder facts(FactStorePort value) {
            this.facts = value;
            return this;
        }

        /** The chat-bound browsers (#141); {@code null}: no browser tools. */
        public Builder browsers(com.aresstack.enterpriseai.browser.api.ChatBrowserPort value) {
            this.browsers = value;
            return this;
        }

        public Builder memories(com.aresstack.enterpriseai.memory.api.MemoryStorePort value) {
            this.memories = value;
            return this;
        }

        public Builder rawIndex(RawKnowledgeIndexPort value) {
            this.rawIndex = value;
            return this;
        }

        public Builder sources(KnowledgeSourceCatalog value) {
            this.sources = value;
            return this;
        }

        public Builder secrets(SecretProvider value) {
            this.secrets = value;
            return this;
        }

        public Builder agent(AgentBackend value) {
            this.agent = value;
            return this;
        }

        /** Baut das eigene Agent-Kommando je Sitzung aus den aktuellen Einstellungen (siehe {@link #agentFor}). */
        public Builder researchAgent(AgentBackend value) {
            this.researchAgent = value;
            return this;
        }

        public Builder agents(Function<AgentConfig, AgentBackend> value) {
            this.agents = value;
            return this;
        }

        public Builder sourceProvider(KnowledgeSourceProvider value) {
            if (value == null) {
                throw new IllegalArgumentException("provider must not be null");
            }
            this.sourceProviders.add(value);
            return this;
        }

        /** Die bisher registrierten Quellen-Ports (für die Start-Quellen). */
        List<KnowledgeSourceProvider> sourceProviders() {
            return Collections.unmodifiableList(sourceProviders);
        }

        public Builder sourceWarning(String value) {
            this.sourceWarnings.add(value);
            return this;
        }

        /** Eine Ressource, die beim Beenden geschlossen wird, in Aufrufreihenfolge. */
        public Builder closing(String name, Closeable closeable) {
            if (name == null || closeable == null) {
                throw new IllegalArgumentException("name and closeable must not be null");
            }
            resources.add(new NamedResource(name, closeable));
            return this;
        }

        public ApplicationPorts build() {
            return new ApplicationPorts(this);
        }
    }
}
