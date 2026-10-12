package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechChunking;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelSelections;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
     * Früher die eine Stimme für jedes TTS-Modell ({@code voice} bei {@code /audio/speech}). Gültige Stimmen hängen vom
     * Modell ab: gilt nur noch, wenn für das Modell keine Stimme in {@link #SPEECH_VOICES_KEY} steht und die Stimme zu
     * ihm passt; der Einstellungsdialog übernimmt sie beim Speichern für das gewählte Modell.
     */
    public static final String SPEECH_VOICE_KEY = "speech.voice";

    /**
     * Die Stimme je TTS-Modell: {@code <katalog>:<modell>=<stimme>;…}, z. B.
     * {@code kipitz:orpheus-tts-3b-de=thomas;kipitz:qwen3-tts=Vivian}.
     */
    public static final String SPEECH_VOICES_KEY = "speech.voices";

    /**
     * Merlins Stimme im Einrichtungsassistenten ({@code [<katalog>:]<modell>}), getrennt vom TTS-Modell des Chats;
     * Vorgabe aus {@code merlin.properties} neben dieser Klasse.
     */
    public static final String MERLIN_VOICE_KEY = "setup.merlin.ttsModel";
    /**
     * Merlins fester Sprecher dieses Modells ({@code voice} der Anfrage), damit er nicht je Absatz anders klingt; nie
     * {@code speech.voice} des Chats. Vorgabe aus {@code merlin.properties}.
     */
    public static final String MERLIN_SPEAKER_KEY = "setup.merlin.ttsVoice";
    static final String MERLIN_RESOURCE = "merlin.properties";

    /** Katalog der lokal installierten Modelle (Stimmen, Embeddings, Chat): Auswahl {@code local:<ordner>}. */
    public static final String LOCAL_CATALOG_ID = "local";

    private final ModelSelections selections;
    private final boolean readAloudAutoStart;
    private final SpeechChunking readAloudChunking;
    private final int synthesisWorkers;
    private final String speechVoice;
    private final Map<String, String> speechVoices;
    private final Path localModelRoot;
    private final ModelReference merlinVoice;
    private final String merlinSpeaker;

    ModelsConfig(ModelSelections selections, boolean readAloudAutoStart, SpeechChunking readAloudChunking,
                 int synthesisWorkers, String speechVoice, Path localModelRoot) {
        this(selections, readAloudAutoStart, readAloudChunking, synthesisWorkers, speechVoice,
                Collections.<String, String>emptyMap(), localModelRoot);
    }

    ModelsConfig(ModelSelections selections, boolean readAloudAutoStart, SpeechChunking readAloudChunking,
                 int synthesisWorkers, String speechVoice, Map<String, String> speechVoices, Path localModelRoot) {
        this(selections, readAloudAutoStart, readAloudChunking, synthesisWorkers, speechVoice, speechVoices,
                localModelRoot, parse(defaultMerlinVoice()), defaultMerlinSpeaker());
    }

    ModelsConfig(ModelSelections selections, boolean readAloudAutoStart, SpeechChunking readAloudChunking,
                 int synthesisWorkers, String speechVoice, Map<String, String> speechVoices, Path localModelRoot,
                 ModelReference merlinVoice, String merlinSpeaker) {
        if (localModelRoot == null) {
            throw new IllegalArgumentException("localModelRoot must not be null");
        }
        this.localModelRoot = localModelRoot;
        this.speechVoice = speechVoice == null || speechVoice.trim().isEmpty() ? null : speechVoice.trim();
        this.speechVoices = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                speechVoices == null ? Collections.<String, String>emptyMap() : speechVoices));
        this.selections = selections == null ? ModelSelections.none() : selections;
        this.readAloudAutoStart = readAloudAutoStart;
        this.readAloudChunking = readAloudChunking == null ? SpeechChunking.PARAGRAPHS : readAloudChunking;
        this.synthesisWorkers = synthesisWorkers;
        this.merlinVoice = merlinVoice;
        this.merlinSpeaker = merlinSpeaker == null ? "" : merlinSpeaker.trim();
    }

    /** Die Vorgabe für Merlins Stimme aus {@code merlin.properties}; leer, wenn die Datei fehlt. */
    public static String defaultMerlinVoice() {
        return merlinDefault(MERLIN_VOICE_KEY);
    }

    /** Die Vorgabe für Merlins Sprecher aus {@code merlin.properties}; leer, wenn die Datei fehlt. */
    public static String defaultMerlinSpeaker() {
        return merlinDefault(MERLIN_SPEAKER_KEY);
    }

    private static String merlinDefault(String key) {
        Properties defaults = new Properties();
        InputStream in = ModelsConfig.class.getResourceAsStream(MERLIN_RESOURCE);
        if (in == null) {
            return "";
        }
        try {
            defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "";
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nur gelesen
            }
        }
        return defaults.getProperty(key, "").trim();
    }

    /** Merlins Stimme ({@link #MERLIN_VOICE_KEY}) oder {@code null}: dann spricht Merlin nicht, nur Sprechblasen. */
    public ModelReference merlinVoice() {
        return merlinVoice;
    }

    /** Merlins Sprecher ({@link #MERLIN_SPEAKER_KEY}); leer = Vorgabe des Dienstes. */
    public String merlinSpeaker() {
        return merlinSpeaker;
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

    /** Parallele Synthesen beim Vorlesen. */
    public int synthesisWorkers() {
        return synthesisWorkers;
    }

    /** Die frühere globale Stimme ({@code speech.voice}) oder {@code null}. */
    public String speechVoice() {
        return speechVoice;
    }

    /** Die gespeicherten Stimmen je Modell ({@code <katalog>:<modell>} → Stimme). */
    public Map<String, String> speechVoices() {
        return speechVoices;
    }

    /** Die für dieses Modell gespeicherte Stimme oder {@code null}. */
    public String speechVoice(ModelReference model) {
        return model == null ? null : speechVoices.get(model.key());
    }

    /**
     * Die Stimme, mit der dieses TTS-Modell spricht: die für das Modell gespeicherte, sonst die frühere globale
     * {@code speech.voice}, wenn sie zum Modell passt (oder der Katalog das Modell nicht kennt), sonst die
     * Standardstimme des Modells; {@code null} nur, wenn nichts davon greift. Lokale Stimmen haben genau einen
     * Sprecher: dort {@code null}.
     */
    public String ttsVoice(ModelReference model, TtsVoiceCatalog catalog) {
        return resolveTtsVoice(model, speechVoice(model), speechVoice, catalog);
    }

    /**
     * Wie {@link #ttsVoice}, mit gespeicherter Stimme des Modells und früherer globaler Stimme als Werte (für den
     * Einstellungsdialog, der einen Entwurf zeigt).
     */
    public static String resolveTtsVoice(ModelReference model, String stored, String speechVoice,
                                         TtsVoiceCatalog catalog) {
        if (model == null || LOCAL_CATALOG_ID.equals(model.catalogId())) {
            return null;
        }
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }
        String legacy = speechVoice == null || speechVoice.trim().isEmpty() ? null : speechVoice.trim();
        boolean known = catalog != null && catalog.knows(model.modelId());
        if (legacy != null && (!known || catalog.voices(model.modelId()).contains(legacy))) {
            return legacy;
        }
        return known ? catalog.defaultVoice(model.modelId()) : null;
    }

    /** Liest {@code <katalog>:<modell>=<stimme>;…}; Modelle ohne Katalog gelten als Enterprise-API. */
    public static Map<String, String> parseSpeechVoices(String text) {
        Map<String, String> voices = new LinkedHashMap<String, String>();
        if (text == null) {
            return voices;
        }
        for (String entry : text.split(";")) {
            int split = entry.lastIndexOf('=');
            if (split <= 0) {
                continue;
            }
            ModelReference model = parse(entry.substring(0, split));
            String voice = entry.substring(split + 1).trim();
            if (model != null && !voice.isEmpty()) {
                voices.put(model.key(), voice);
            }
        }
        return voices;
    }

    /** Schreibt die Stimmen je Modell als {@code <katalog>:<modell>=<stimme>;…}; leer, wenn keine. */
    public static String formatSpeechVoices(Map<String, String> voices) {
        StringBuilder text = new StringBuilder();
        if (voices != null) {
            for (Map.Entry<String, String> entry : voices.entrySet()) {
                ModelReference model = parse(entry.getKey());
                String voice = entry.getValue() == null ? "" : entry.getValue().trim();
                if (model == null || voice.isEmpty()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append(';');
                }
                text.append(model.key()).append('=').append(voice);
            }
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "ModelsConfig[" + selections + ", local=" + localModelRoot + ", readAloudAutoStart="
                + readAloudAutoStart + "]";
    }
}
