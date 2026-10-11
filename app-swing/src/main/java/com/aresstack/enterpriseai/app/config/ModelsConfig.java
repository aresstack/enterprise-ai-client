package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.speech.SpeechChunking;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelSelections;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Modellverwaltung: die eine Auswahl je {@link ModelCategory} und das Modellverzeichnis der lokalen Stimmen. Chat und
 * Embeddings stehen weiter unter {@code chat.model} und {@code embedding.model}, alle anderen Kategorien unter
 * {@code model.<kategorie>}; Werte sind {@code [<katalog>:]<modell>}, ohne Katalog gilt die Enterprise-API.
 */
public final class ModelsConfig {

    /** Präfix der Kategorien ohne eigenen Abschnitt ({@code model.rerank}, {@code model.tts}, ...). */
    public static final String KEY_PREFIX = "model.";

    /** Neue Antworten automatisch vorlesen (askai arch {@code readAloudAutoStart}); braucht ein TTS-Modell. */
    public static final String READ_ALOUD_AUTO_START_KEY = "speech.readAloud.autoStart";

    /** Einheit je Synthese (askai arch {@code tts.chunking}): ANSWER, PARAGRAPHS (Standard) oder SENTENCES. */
    public static final String READ_ALOUD_CHUNKING_KEY = "speech.readAloud.chunking";

    /** Parallele Synthesen beim Vorlesen (askai arch {@code tts.synthesisWorkers}), 1 bis 8, Standard 2. */
    public static final String READ_ALOUD_WORKERS_KEY = "speech.readAloud.synthesisWorkers";

    /**
     * Embedding-Modell, mit dem die Einheit „Absätze“ beim Vorlesen nach Thema umbricht: {@code local:<ordner>}
     * (Standard: lokal) oder {@link #READ_ALOUD_EMBEDDING_CLOUD}; leer = Absätze nur an Leerzeilen.
     */
    public static final String READ_ALOUD_EMBEDDING_KEY = "speech.readAloud.embeddingModel";

    /** Wert von {@link #READ_ALOUD_EMBEDDING_KEY}: das Cloud-Embedding-Modell der Wissensbasis. */
    public static final String READ_ALOUD_EMBEDDING_CLOUD = "cloud";

    /** Stimme der Enterprise-Sprachausgabe ({@code voice} bei {@code /audio/speech}); leer = ohne. */
    public static final String SPEECH_VOICE_KEY = "speech.voice";

    /** Katalog der lokal installierten Modelle (Stimmen, Embeddings, Chat): Auswahl {@code local:<ordner>}. */
    public static final String LOCAL_CATALOG_ID = "local";

    private final ModelSelections selections;
    private final boolean readAloudAutoStart;
    private final SpeechChunking readAloudChunking;
    private final int synthesisWorkers;
    private final String readAloudEmbedding;
    private final String speechVoice;
    private final Path localModelRoot;

    ModelsConfig(ModelSelections selections, boolean readAloudAutoStart, SpeechChunking readAloudChunking,
                 int synthesisWorkers, String readAloudEmbedding, String speechVoice, Path localModelRoot) {
        if (localModelRoot == null) {
            throw new IllegalArgumentException("localModelRoot must not be null");
        }
        this.localModelRoot = localModelRoot;
        this.speechVoice = speechVoice == null || speechVoice.trim().isEmpty() ? null : speechVoice.trim();
        this.selections = selections == null ? ModelSelections.none() : selections;
        this.readAloudAutoStart = readAloudAutoStart;
        this.readAloudChunking = readAloudChunking == null ? SpeechChunking.PARAGRAPHS : readAloudChunking;
        this.synthesisWorkers = synthesisWorkers;
        this.readAloudEmbedding = readAloudEmbedding == null || readAloudEmbedding.trim().isEmpty() ? null
                : readAloudEmbedding.trim();
    }

    /** Die bekannten Kataloge (Präfixe einer Auswahl). */
    public static List<String> catalogIds() {
        return Collections.unmodifiableList(Arrays.asList(KipitzModelCatalogAdapter.CATALOG_ID, LOCAL_CATALOG_ID));
    }

    /** Der Katalog einer Auswahl ohne Präfix: die Enterprise-API. */
    public static String defaultCatalogId() {
        return KipitzModelCatalogAdapter.CATALOG_ID;
    }

    /** Liest {@code [<katalog>:]<modell>}; {@code null} bei leerem Text. */
    public static ModelReference parse(String text) {
        return ModelReference.parse(text, catalogIds(), defaultCatalogId());
    }

    /** Der Konfigurationsschlüssel einer Kategorie. */
    public static String keyOf(ModelCategory category) {
        switch (category) {
            case CHAT:
                return "chat.model";
            case EMBEDDING:
                return "embedding.model";
            default:
                return KEY_PREFIX + category.key();
        }
    }

    public ModelSelections selections() {
        return selections;
    }

    /** Modellverzeichnis der lokalen Modelle ({@code models.local.modelRoot}), ein Ordner je Stimme. */
    public Path localModelRoot() {
        return localModelRoot;
    }

    /** Ob neue Antworten automatisch vorgelesen werden. */
    public boolean readAloudAutoStart() {
        return readAloudAutoStart;
    }

    /** Einheit, in der vorgelesen wird. */
    public SpeechChunking readAloudChunking() {
        return readAloudChunking;
    }

    /** Embedding-Modell der Absätze beim Vorlesen ({@code local:<ordner>} oder {@code cloud}) oder {@code null}. */
    public String readAloudEmbedding() {
        return readAloudEmbedding;
    }

    /** Parallele Synthesen beim Vorlesen. */
    public int synthesisWorkers() {
        return synthesisWorkers;
    }

    /** Stimme für die Sprachausgabe der Enterprise-API ({@code speech.voice}) oder {@code null}. */
    public String speechVoice() {
        return speechVoice;
    }

    @Override
    public String toString() {
        return "ModelsConfig[" + selections + ", local=" + localModelRoot + ", readAloudAutoStart="
                + readAloudAutoStart + "]";
    }
}
