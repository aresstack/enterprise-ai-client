package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.settings.ModelCatalogCache;
import com.aresstack.enterpriseai.proasteion.application.settings.ModelCatalogLoader;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.UnifiedModelCatalog;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRecognitionRegistry;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechSynthesisRegistry;
import com.aresstack.enterpriseai.model.api.ModelCatalogPort;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogConfig;
import com.aresstack.enterpriseai.model.kipitz.KipitzSpeechAdapter;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.local.LocalVoiceSpeechAdapter;
import com.aresstack.enterpriseai.speech.onnx.OnnxSpeechRuntime;
import com.aresstack.enterpriseai.text.local.LocalSpeechRecognitionAdapter;
import com.aresstack.enterpriseai.text.onnx.OnnxAudioLanguageModelRuntime;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Baut die Modellquelle aus der Konfiguration: die Enterprise-API (KIPITZ, {@code GET /models}); lokale Modelle sind
 * die installierten Stimmen im Modellverzeichnis, die der Dialog selbst listet. Jeder Stand landet im
 * {@link ModelCatalogCache}. Eine Instanz dient beim Start der Hintergrundabfrage und im Einstellungen-Dialog dem
 * Reiter „Modelle“; sie hält das Inferenz-Backend der lokalen Stimmen, das mit {@link #close()} endet.
 */
public final class ModelCatalogs implements ModelCatalogLoader, Closeable {

    private static final Logger LOG = Logger.getLogger(ModelCatalogs.class.getName());

    /** Liefert für eine Konfiguration das Bearer-Token des Chats (darf blockieren, etwa für das KeePass-Pairing). */
    public interface TokenLookup {
        String token(AppConfig config) throws IOException;
    }

    private final ModelCatalogCache cache;
    private final TokenLookup dialogTokens;
    /** Pilot-Backend der lokalen Stimmen im Prozess; eins für die ganze Anwendung, geladen beim ersten Vorlesen. */
    private final OnnxSpeechRuntime localSpeechRuntime = new OnnxSpeechRuntime();
    /** Pilot-Backend der lokalen Spracheingabe im Prozess; geladen beim ersten Diktat. */
    private final OnnxAudioLanguageModelRuntime localRecognitionRuntime = new OnnxAudioLanguageModelRuntime();
    private final Object refreshLock = new Object();

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
     * Fragt die Quelle ab (blockiert, nie auf dem EDT), speichert den Stand und liefert ihn. Ist sie nicht
     * erreichbar, bleiben die zuletzt bekannten Modelle.
     */
    public ModelCatalogSnapshot refresh(AppConfig config, NetworkServices network, Supplier<String> token) {
        synchronized (refreshLock) {
            List<ModelCatalogPort> catalogs = new ArrayList<ModelCatalogPort>();
            catalogs.add(new KipitzModelCatalogAdapter(kipitz(config, network, token)));
            ModelCatalogSnapshot snapshot = new UnifiedModelCatalog(catalogs, cache.read()).refresh();
            LOG.info("Modellkatalog: " + snapshot);
            cache.write(snapshot);
            return snapshot;
        }
    }

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

    /**
     * Die Sprachausgabe je Modellquelle: die Enterprise-API (KIPITZ) und die lokalen Stimmen im Prozess, beide
     * immer. Welcher Port spricht, entscheidet der Katalog des gewählten TTS-Modells ({@code model.tts}).
     *
     * @param connection Verbindung der Enterprise-API (die des laufenden Graphen, passend zu Netz und Token)
     * @param models     Stimme und Modellverzeichnis, auch frisch gespeichert
     */
    public SpeechSynthesisRegistry speech(AppConfig connection, ModelsConfig models, NetworkServices network,
                                          Supplier<String> token) {
        List<SpeechSynthesisPort> ports = new ArrayList<SpeechSynthesisPort>();
        ports.add(new KipitzSpeechAdapter(kipitz(connection, network, token), models.speechVoice()));
        // Lokale Stimmen sprechen im Prozess (ONNX Runtime als Pilot), ohne externes Programm.
        ports.add(new LocalVoiceSpeechAdapter(ModelsConfig.LOCAL_CATALOG_ID, models.localModelRoot(),
                localSpeechRuntime));
        return new SpeechSynthesisRegistry(ports);
    }

    /**
     * Die Spracheingabe je Modellquelle: lokal installierte Modelle mit Audio-Eingang im Prozess. Die Enterprise-API
     * hat keine Spracheingabe; liefert ihr Katalog einmal ein STT-Modell, kommt hier ihr Adapter dazu. Welcher Port
     * erkennt, entscheidet der Katalog des gewählten STT-Modells ({@code model.stt}).
     */
    public SpeechRecognitionRegistry speechRecognition(ModelsConfig models) {
        List<SpeechRecognitionPort> ports = new ArrayList<SpeechRecognitionPort>();
        ports.add(new LocalSpeechRecognitionAdapter(ModelsConfig.LOCAL_CATALOG_ID, models.localModelRoot(),
                localRecognitionRuntime));
        return new SpeechRecognitionRegistry(ports);
    }

    /** Gibt geladene Stimmen und Spracheingabe-Modelle frei; idempotent. */
    @Override
    public void close() {
        localSpeechRuntime.close();
        localRecognitionRuntime.close();
    }
}
