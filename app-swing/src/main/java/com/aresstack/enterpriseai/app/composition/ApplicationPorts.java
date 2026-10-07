package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.security.api.SecretProvider;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alle Ports, gegen die die Anwendung gebaut wird, unveränderlich. Produktiv liefert sie
 * {@link AdapterAssembly} mit echten Adaptern, der Kompositionstest mit Fakes; {@link CompositionRoot} kennt
 * den Unterschied nicht. {@link #close()} schließt die mitgegebenen Ressourcen in Reihenfolge (zuletzt der
 * Index) und ist idempotent.
 */
public final class ApplicationPorts implements Closeable {

    private static final Logger LOG = Logger.getLogger(ApplicationPorts.class.getName());

    private final ChatCompletionPort chat;
    private final EmbeddingPort embeddings;
    private final EmbeddingModelIdentity embeddingSpace;
    private final KnowledgeIndexPort index;
    private final KnowledgeSourceCatalog sources;
    private final SecretProvider secrets;
    private final AgentBackend agent;
    private final List<NamedResource> resources;
    private boolean closed;

    private ApplicationPorts(Builder builder) {
        if (builder.chat == null || builder.embeddings == null || builder.embeddingSpace == null
                || builder.index == null || builder.secrets == null) {
            throw new IllegalArgumentException("chat, embeddings, embeddingSpace, index and secrets must be given");
        }
        this.chat = builder.chat;
        this.embeddings = builder.embeddings;
        this.embeddingSpace = builder.embeddingSpace;
        this.index = builder.index;
        this.sources = builder.sources == null ? KnowledgeSourceCatalog.empty() : builder.sources;
        this.secrets = builder.secrets;
        this.agent = builder.agent;
        this.resources = Collections.unmodifiableList(new ArrayList<NamedResource>(builder.resources));
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

    public EmbeddingModelIdentity embeddingSpace() {
        return embeddingSpace;
    }

    public KnowledgeIndexPort index() {
        return index;
    }

    public KnowledgeSourceCatalog sources() {
        return sources;
    }

    public SecretProvider secrets() {
        return secrets;
    }

    /** {@code null}: kein Agent-Modus konfiguriert. */
    public AgentBackend agent() {
        return agent;
    }

    public boolean hasAgent() {
        return agent != null;
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
        private EmbeddingModelIdentity embeddingSpace;
        private KnowledgeIndexPort index;
        private KnowledgeSourceCatalog sources;
        private SecretProvider secrets;
        private AgentBackend agent;
        private final List<NamedResource> resources = new ArrayList<NamedResource>();

        private Builder() {
        }

        public Builder chat(ChatCompletionPort value) {
            this.chat = value;
            return this;
        }

        public Builder embeddings(EmbeddingPort value, EmbeddingModelIdentity space) {
            this.embeddings = value;
            this.embeddingSpace = space;
            return this;
        }

        public Builder index(KnowledgeIndexPort value) {
            this.index = value;
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
