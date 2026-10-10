package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.settings.ModelCatalogCache;
import com.aresstack.enterpriseai.app.settings.ModelCatalogLoader;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.application.modelcatalog.UnifiedModelCatalog;
import com.aresstack.enterpriseai.application.speech.SpeechSynthesisRegistry;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.model.api.ModelCatalogPort;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogConfig;
import com.aresstack.enterpriseai.model.kipitz.KipitzSpeechAdapter;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarEmbeddingAdapter;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarModelCatalogAdapter;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Baut die Modellquellen aus der Konfiguration und führt sie über {@link UnifiedModelCatalog} zusammen: die
 * Enterprise-API (KIPITZ, {@code GET /models}) immer, den lokalen Java-21-Sidecar nur, wenn er konfiguriert ist.
 * Der Sidecar-Prozess gehört dieser Klasse (einer für die ganze Anwendung, neu gestartet, wenn sich seine Pfade
 * ändern) und endet mit {@link #close()}. Jeder Stand landet im {@link ModelCatalogCache}. Eine Instanz dient beim
 * Start der Hintergrundabfrage und im Einstellungen-Dialog dem Reiter „Modelle“.
 */
public final class ModelCatalogs implements ModelCatalogLoader, LocalModelRuntime, Closeable {

    private static final Logger LOG = Logger.getLogger(ModelCatalogs.class.getName());

    /** Liefert für eine Konfiguration das Bearer-Token des Chats (darf blockieren, etwa für das KeePass-Pairing). */
    public interface TokenLookup {
        String token(AppConfig config) throws IOException;
    }

    private final ModelCatalogCache cache;
    private final TokenLookup dialogTokens;
    private LocalSidecarModelCatalogAdapter sidecar;
    private boolean closed;

    /**
     * @param cache        Zwischenspeicher neben der Konfiguration
     * @param dialogTokens Token für Abfragen aus dem Dialog (Entwurf); produktiv KeePass mit Pairing-Dialog
     */
    public ModelCatalogs(ModelCatalogCache cache, TokenLookup dialogTokens) {
        if (cache == null || dialogTokens == null) {
            throw new IllegalArgumentException("cache and dialogTokens must not be null");
        }
        this.cache = cache;
        this.dialogTokens = dialogTokens;
    }

    @Override
    public ModelCatalogSnapshot cached() {
        return cache.read();
    }

    /** Abfrage für den Dialog: Netz und Token aus dem Entwurf. */
    @Override
    public ModelCatalogSnapshot refresh(final AppConfig config) {
        return refresh(config, NetworkServices.from(config.network()), new Supplier<String>() {
            @Override
            public String get() {
                try {
                    return dialogTokens.token(config);
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Fragt alle Quellen ab (blockiert, nie auf dem EDT), speichert den Stand und liefert ihn. Nicht erreichbare
     * Quellen behalten ihre zuletzt bekannten Modelle.
     */
    public ModelCatalogSnapshot refresh(AppConfig config, NetworkServices network, Supplier<String> token) {
        synchronized (refreshLock) {
            return refreshNow(config, network, token);
        }
    }

    private ModelCatalogSnapshot refreshNow(AppConfig config, NetworkServices network, Supplier<String> token) {
        List<ModelCatalogPort> catalogs = new ArrayList<ModelCatalogPort>();
        catalogs.add(new KipitzModelCatalogAdapter(kipitz(config, network, token)));
        LocalSidecarModelCatalogAdapter local = sidecar(config.models().localSidecar());
        if (local != null) {
            catalogs.add(local);
        }
        ModelCatalogSnapshot snapshot = new UnifiedModelCatalog(catalogs, cache.read()).refresh();
        if (local != null) {
            stopIfReleased(local);
        }
        LOG.info("Modellkatalog: " + snapshot);
        cache.write(snapshot);
        return snapshot;
    }

    private final Object refreshLock = new Object();

    private static KipitzModelCatalogConfig kipitz(AppConfig config, NetworkServices network,
                                                   Supplier<String> token) {
        return KipitzModelCatalogConfig.builder(config.chat().baseUrl())
                .bearerToken(token)
                .routes(network.routes())
                .sslSocketFactory(network.tls())
                .userAgent(network.userAgent())
                .connectTimeoutMillis(config.chat().connectTimeoutMillis())
                .readTimeoutMillis(Math.min(60000, Math.max(5000, config.chat().readTimeoutMillis())))
                .build();
    }

    private synchronized LocalSidecarModelCatalogAdapter sidecar(LocalSidecarConfig config) {
        if (closed) {
            return null;
        }
        if (sidecar != null && (config == null || !sidecar.config().equals(config))) {
            sidecar.close();
            sidecar = null;
        }
        if (sidecar == null && config != null) {
            sidecar = new LocalSidecarModelCatalogAdapter(config);
        }
        return sidecar;
    }

    /** Ein während der Abfrage ersetzter oder geschlossener Sidecar darf nicht weiterlaufen. */
    private synchronized void stopIfReleased(LocalSidecarModelCatalogAdapter local) {
        if (local != sidecar) {
            local.close();
        }
    }

    /**
     * Die Sprachausgabe je Modellquelle, wie die Quellen selbst: die Enterprise-API (KIPITZ) immer, der lokale
     * Sidecar nur, wenn er konfiguriert ist. Welcher Port spricht, entscheidet der Katalog des gewählten TTS-Modells
     * ({@code model.tts}); lokale und Enterprise-Modelle sind gleichwertig.
     *
     * @param connection Verbindung der Enterprise-API (die des laufenden Graphen, passend zu Netz und Token)
     * @param models     Stimme und lokaler Sidecar, auch frisch gespeichert
     */
    public SpeechSynthesisRegistry speech(AppConfig connection, ModelsConfig models, NetworkServices network,
                                          Supplier<String> token) {
        List<SpeechSynthesisPort> ports = new ArrayList<SpeechSynthesisPort>();
        ports.add(new KipitzSpeechAdapter(kipitz(connection, network, token), models.speechVoice()));
        if (models.localSidecar() != null) {
            ports.add(localSpeech(models.localSidecar()));
        }
        return new SpeechSynthesisRegistry(ports);
    }

    /**
     * Die Sprachausgabe des lokalen Sidecars für diese Pfade: teilt sich den Prozess mit dem Katalog (ein Sidecar
     * für die ganze Anwendung) und startet ihn erst beim ersten Vorlesen. Nach {@link #close()} scheitert sie.
     */
    private SpeechSynthesisPort localSpeech(final LocalSidecarConfig config) {
        return new SpeechSynthesisPort() {
            @Override
            public String catalogId() {
                return LocalSidecarModelCatalogAdapter.CATALOG_ID;
            }

            @Override
            public SynthesizedSpeech synthesize(String modelId, String text) throws SpeechSynthesisException {
                LocalSidecarModelCatalogAdapter local = sidecar(config);
                if (local == null) {
                    throw new SpeechSynthesisException("Lokaler Sidecar ist beendet");
                }
                return local.speechSynthesis().synthesize(modelId, text);
            }
        };
    }

    /** Chat des lokalen Sidecars für diese Pfade (geteilter Prozess, Start bei der ersten Anfrage). */
    @Override
    public ChatCompletionPort chat(final LocalSidecarConfig config) {
        return new ChatCompletionPort() {
            @Override
            public ChatResponse complete(ChatRequest request) {
                return running(config).chat().complete(request);
            }

            @Override
            public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
                return running(config).chat().stream(request, listener);
            }
        };
    }

    /** Embeddings des lokalen Sidecars für diese Pfade (geteilter Prozess, Start bei der ersten Anfrage). */
    @Override
    public EmbeddingPort embeddings(final LocalSidecarConfig config, final String modelId, final int dimension) {
        final EmbeddingModelIdentity identity = LocalSidecarEmbeddingAdapter.identity(modelId, dimension);
        return new EmbeddingPort() {
            @Override
            public EmbeddingModelIdentity modelIdentity() {
                return identity;
            }

            @Override
            public EmbeddingBatch embed(List<String> texts) {
                LocalSidecarModelCatalogAdapter local = sidecar(config);
                if (local == null) {
                    throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Lokaler Sidecar ist beendet");
                }
                return local.embeddings(modelId, dimension).embed(texts);
            }
        };
    }

    private LocalSidecarModelCatalogAdapter running(LocalSidecarConfig config) {
        LocalSidecarModelCatalogAdapter local = sidecar(config);
        if (local == null) {
            throw new ChatCompletionException(ChatErrorKind.TRANSPORT, "Lokaler Sidecar ist beendet");
        }
        return local;
    }

    /** Beendet den Sidecar-Prozess; idempotent. */
    @Override
    public synchronized void close() {
        closed = true;
        if (sidecar != null) {
            sidecar.close();
            sidecar = null;
        }
    }
}
