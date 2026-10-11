package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.application.modelexecution.ChatModelExecutorRegistry;
import com.aresstack.enterpriseai.application.modelexecution.EmbeddingModelExecutorRegistry;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ResponsesPort;
import com.aresstack.enterpriseai.chat.api.ResponsesRequest;
import com.aresstack.enterpriseai.chat.api.ResponsesResult;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.text.local.LocalChatAdapter;
import com.aresstack.enterpriseai.text.local.LocalEmbeddingAdapter;
import com.aresstack.enterpriseai.text.onnx.OnnxCausalLmRuntime;
import com.aresstack.enterpriseai.text.onnx.OnnxTextEncoderRuntime;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chat-, Responses- und Embedding-Port der Anwendung, die Einstellungen ohne Neustart übernehmen: Use Cases und
 * Oberfläche halten diese drei Ports; jeder Aufruf geht an die Adapter des aktuellen Stands. {@link #apply} baut
 * die Adapter aus den gespeicherten Einstellungen neu (Basis-URL, Modellauswahl je Kategorie, API-Key-Eintrag,
 * Proxy, TLS, User-Agent, lokaler Modellordner). Laufende Anfragen enden auf dem alten Adapter; die nächste nimmt
 * den neuen. Die ONNX-Laufzeiten für lokale Modelle bleiben dieselben (sie laden je Modelldatei) und werden erst
 * beim Beenden geschlossen.
 */
final class LiveModelPorts implements ApplicationPorts.Reconfigurable, Closeable {

    private static final Logger LOG = Logger.getLogger(LiveModelPorts.class.getName());

    private final SecretProvider secrets;
    private final OnnxCausalLmRuntime decoder = new OnnxCausalLmRuntime();
    private final OnnxTextEncoderRuntime encoder = new OnnxTextEncoderRuntime();
    private volatile Adapters current;

    private final ChatCompletionPort chat = new ChatCompletionPort() {
        @Override
        public ChatResponse complete(ChatRequest request) {
            return current.chat.complete(request);
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            return current.chat.stream(request, listener);
        }

        @Override
        public String toString() {
            return "LiveChat[" + current.chat + "]";
        }
    };

    private final ResponsesPort responses = new ResponsesPort() {
        @Override
        public ResponsesResult create(ResponsesRequest request) {
            return current.responses.create(request);
        }

        @Override
        public String toString() {
            return "LiveResponses[" + current.responses + "]";
        }
    };

    private final EmbeddingPort readAloudEmbeddings = new EmbeddingPort() {
        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return current().modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            return current().embed(texts);
        }

        private EmbeddingPort current() {
            EmbeddingPort port = current.readAloud;
            if (port == null) {
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "kein Embedding-Modell für die "
                        + "Absätze gewählt oder das lokale Modell fehlt (Einstellungen → Sprachausgabe)");
            }
            return port;
        }

        @Override
        public String toString() {
            return "LiveReadAloudEmbeddings[" + current.readAloud + "]";
        }
    };

    private final EmbeddingPort embeddings = new EmbeddingPort() {
        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return current.embeddings.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            return current.embeddings.embed(texts);
        }

        @Override
        public String toString() {
            return "LiveEmbeddings[" + current.embeddings + "]";
        }
    };

    LiveModelPorts(AppConfig config, NetworkServices network, SecretProvider secrets) {
        if (config == null || network == null || secrets == null) {
            throw new IllegalArgumentException("config, network and secrets must not be null");
        }
        this.secrets = secrets;
        this.current = build(config, network);
    }

    ChatCompletionPort chat() {
        return chat;
    }

    ResponsesPort responses() {
        return responses;
    }

    /** Liefert je Aufruf die Welt des aktuellen Embedding-Modells; der Index-Namensraum folgt ihr. */
    EmbeddingPort embeddings() {
        return embeddings;
    }

    /** Das Embedding-Modell der Absätze beim Vorlesen, je Aufruf das aktuelle; ohne Wahl scheitert {@code embed}. */
    EmbeddingPort readAloudEmbeddings() {
        return readAloudEmbeddings;
    }

    @Override
    public void apply(AppConfig config, NetworkServices network) {
        current = build(config, network);
        LOG.info("Modelle neu gebunden: Chat " + config.models().selections().get(ModelCategory.CHAT)
                + ", Embeddings " + current.embeddings.modelIdentity());
    }

    @Override
    public void close() {
        decoder.close();
        encoder.close();
    }

    private Adapters build(AppConfig config, NetworkServices network) {
        // Ausführung je Katalog der Modellauswahl: die Enterprise-API immer; lokal installierte Modelle laufen im
        // Prozess über ONNX Runtime (Chat und Embeddings hier, Stimmen in ModelCatalogs).
        OpenAiCompatibleChatAdapter chatAdapter = AdapterAssembly.chat(config.chat(), secrets, network);
        Map<String, ChatCompletionPort> chats = new LinkedHashMap<String, ChatCompletionPort>();
        chats.put(KipitzModelCatalogAdapter.CATALOG_ID, chatAdapter);
        // lokale Chat-Modelle: geladen erst bei der ersten Anfrage, Modell = Ordnername der Auswahl
        chats.put(ModelsConfig.LOCAL_CATALOG_ID, new LocalChatAdapter(config.models().localModelRoot(), decoder));
        Map<String, ResponsesPort> responsePorts = new LinkedHashMap<String, ResponsesPort>();
        // derselbe Adapter: /responses mit Route, TLS und Token des Chats
        responsePorts.put(KipitzModelCatalogAdapter.CATALOG_ID, chatAdapter);
        ChatModelExecutorRegistry chatModels = new ChatModelExecutorRegistry(chats, responsePorts,
                ModelsConfig.catalogIds(), ModelsConfig.defaultCatalogId(),
                config.models().selections().get(ModelCategory.CHAT));

        Map<String, EmbeddingPort> embeddingPorts = new LinkedHashMap<String, EmbeddingPort>();
        embeddingPorts.put(KipitzModelCatalogAdapter.CATALOG_ID,
                AdapterAssembly.embeddings(config.embedding(), secrets, network));
        ModelReference embeddingModel = config.models().selections().get(ModelCategory.EMBEDDING);
        String localEmbeddingProblem = null;
        if (embeddingModel != null && ModelsConfig.LOCAL_CATALOG_ID.equals(embeddingModel.catalogId())) {
            try {
                embeddingPorts.put(ModelsConfig.LOCAL_CATALOG_ID, new LocalEmbeddingAdapter(
                        ModelsConfig.LOCAL_CATALOG_ID, embeddingModel.modelId(), config.models().localModelRoot(),
                        encoder));
            } catch (IllegalArgumentException missing) {
                localEmbeddingProblem = missing.getMessage();
                LOG.log(Level.WARNING, "Lokales Embedding-Modell nicht nutzbar: {0}", missing.getMessage());
            }
        }
        EmbeddingModelExecutorRegistry embeddingModels = new EmbeddingModelExecutorRegistry(embeddingPorts);
        String embeddingCatalog = embeddingModel == null ? KipitzModelCatalogAdapter.CATALOG_ID
                : embeddingModel.catalogId();
        // Ein lokal gewähltes, aber fehlendes Modell scheitert (Hinweis), statt Text an die Enterprise-API zu geben.
        EmbeddingPort embeddingPort = embeddingModels.supports(embeddingCatalog)
                ? embeddingModels.require(embeddingCatalog)
                : unavailableEmbeddings(EmbeddingModelIdentity.of(embeddingModel.modelId(),
                config.embedding().dimension()).withAttribute("encoding", "float")
                .withAttribute("catalog", embeddingCatalog), localEmbeddingProblem);
        return new Adapters(chatModels.chat(), chatModels.responses(), embeddingPort,
                readAloudEmbeddings(config, network));
    }

    /**
     * Absätze beim Vorlesen: lokal ({@code local:<ordner>}) im Prozess über dieselbe ONNX-Laufzeit, Cloud das
     * Embedding-Modell der Wissensbasis über die Enterprise-API; ohne Wahl oder ohne lokales Modell {@code null}.
     */
    private EmbeddingPort readAloudEmbeddings(AppConfig config, NetworkServices network) {
        String choice = config.models().readAloudEmbedding();
        if (choice == null) {
            return null;
        }
        if (ModelsConfig.READ_ALOUD_EMBEDDING_CLOUD.equals(choice)) {
            return AdapterAssembly.embeddings(config.embedding(), secrets, network);
        }
        ModelReference local = ModelsConfig.parse(choice);
        if (local == null || !ModelsConfig.LOCAL_CATALOG_ID.equals(local.catalogId())) {
            return null;
        }
        try {
            return new LocalEmbeddingAdapter(ModelsConfig.LOCAL_CATALOG_ID, local.modelId(),
                    config.models().localModelRoot(), encoder);
        } catch (IllegalArgumentException missing) {
            LOG.log(Level.WARNING, "Lokales Embedding-Modell für die Absätze nicht nutzbar: {0}", missing.getMessage());
            return null;
        }
    }

    private static EmbeddingPort unavailableEmbeddings(final EmbeddingModelIdentity identity, final String problem) {
        return new EmbeddingPort() {
            @Override
            public EmbeddingModelIdentity modelIdentity() {
                return identity;
            }

            @Override
            public EmbeddingBatch embed(List<String> texts) {
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Lokales Embedding-Modell "
                        + identity.modelId() + " nicht nutzbar" + (problem == null ? "" : ": " + problem)
                        + " (Einstellungen → Modelle → Lokale Modelle)");
            }
        };
    }

    private static final class Adapters {
        final ChatCompletionPort chat;
        final ResponsesPort responses;
        final EmbeddingPort embeddings;
        final EmbeddingPort readAloud;

        Adapters(ChatCompletionPort chat, ResponsesPort responses, EmbeddingPort embeddings, EmbeddingPort readAloud) {
            this.readAloud = readAloud;
            this.chat = chat;
            this.responses = responses;
            this.embeddings = embeddings;
        }
    }
}
