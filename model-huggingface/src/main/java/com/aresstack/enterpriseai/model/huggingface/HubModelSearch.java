package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.huggingface.hub.HuggingFaceHub;
import com.aresstack.huggingface.hub.HuggingFaceHubException;
import com.aresstack.huggingface.hub.model.HubFile;
import com.aresstack.huggingface.hub.model.ModelSearchRequest;
import com.aresstack.huggingface.hub.model.ModelSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sucht auf dem Hugging Face Hub je Kategorie nach Modellen, die der Client ohne Umwandlung im Prozess lädt (ONNX
 * Runtime), und macht aus jedem Treffer die installierbaren Modelle. Sprachausgabe:
 * <ul>
 *   <li>Piper: je Paar {@code <name>.onnx} + {@code <name>.onnx.json} eine Stimme. Folgt der Name dem Piper-Schema
 *   {@code <sprache>_<REGION>-…}, bleiben nur deutsche: andere espeak-Sprachen kann der Client ohne espeak-ng nicht
 *   phonemisieren. Ob die Stimme wirklich passt, prüft die Installation an der {@code .onnx.json}.</li>
 *   <li>VITS im Hugging-Face-Layout: {@code config.json}, {@code vocab.json} und {@code onnx/model.onnx} oder
 *   {@code model.onnx}; {@code model_type=vits} prüft die Installation an der {@code config.json}.</li>
 * </ul>
 * Embeddings ({@code feature-extraction}, {@code sentence-similarity}) und Chat ({@code text-generation}): ONNX-Export
 * mit {@code config.json} und {@code tokenizer.json}; Graph {@code model.onnx} bzw. eine quantisierte Variante (Chat
 * bevorzugt {@code model_q4.onnx}), samt externen Gewichten ({@code <graph>_data}) und den Dateien für Tokenizer,
 * Chat-Vorlage und Pooling. Ob Konfiguration und Tokenizer passen, prüft die Installation vor dem Graphen.
 */
final class HubModelSearch {

    /** Treffer je Hub-Anfrage; die Dateiliste wird nur für diese gelesen. */
    static final int LIMIT = 20;
    /** Treffer je Text-Kategorie (je Treffer eine Anfrage für die Dateiliste). */
    static final int TEXT_LIMIT = 10;
    private static final String[] EMBEDDING_GRAPHS = {"model.onnx", "model_quantized.onnx", "model_int8.onnx",
            "model_uint8.onnx"};
    private static final String[] CHAT_GRAPHS = {"model_q4.onnx", "model_quantized.onnx", "model_int8.onnx",
            "model_uint8.onnx", "model.onnx"};
    private static final String[] TEXT_EXTRAS = {"tokenizer_config.json", "generation_config.json",
            "special_tokens_map.json", "chat_template.jinja", "modules.json", "1_Pooling/config.json",
            "sentence_bert_config.json"};
    private static final Pattern PIPER_NAME = Pattern.compile("^([a-z]{2,3})_[A-Z]{2}-.+");

    private final HuggingFaceHub hub;

    HubModelSearch(HuggingFaceHub hub) {
        this.hub = hub;
    }

    /**
     * Unterstützte Modelle zur Suche: Sprachausgabe, Embeddings und Chat, oder nur die genannte Kategorie
     * ({@code null} = alle drei); leer, wenn nichts passt. Blockiert (Netz).
     */
    List<HuggingFaceVoice> search(String query, ModelCategory category) throws HuggingFaceHubException {
        List<HuggingFaceVoice> found = new ArrayList<HuggingFaceVoice>();
        if (category == null || category == ModelCategory.TTS) {
            found.addAll(voices(query));
        }
        Map<String, ModelSummary> embeddings = new LinkedHashMap<String, ModelSummary>();
        if (category == null || category == ModelCategory.EMBEDDING) {
            collect(embeddings, request(query).task("feature-extraction").filter("onnx"), TEXT_LIMIT);
            collect(embeddings, request(query).task("sentence-similarity").filter("onnx"), TEXT_LIMIT);
        }
        for (ModelSummary repository : embeddings.values()) {
            HuggingFaceVoice model = textModel(repository.getId(), paths(hub.models().model(repository.getId())
                    .files().execute()), ModelCategory.EMBEDDING);
            if (model != null) {
                found.add(model);
            }
        }
        Map<String, ModelSummary> chats = new LinkedHashMap<String, ModelSummary>();
        if (category == null || category == ModelCategory.CHAT) {
            collect(chats, request(query).task("text-generation").filter("onnx"), TEXT_LIMIT);
        }
        for (ModelSummary repository : chats.values()) {
            HuggingFaceVoice model = textModel(repository.getId(), paths(hub.models().model(repository.getId())
                    .files().execute()), ModelCategory.CHAT);
            if (model != null) {
                found.add(model);
            }
        }
        return found;
    }

    private List<HuggingFaceVoice> voices(String query) throws HuggingFaceHubException {
        Map<String, ModelSummary> repositories = new LinkedHashMap<String, ModelSummary>();
        collect(repositories, request(query).task("text-to-speech"), LIMIT * 2);
        collect(repositories, request(query).task("text-to-audio").filter("onnx"), LIMIT * 2);
        collect(repositories, request(query).task("text-to-speech").filter("onnx"), LIMIT * 2);
        List<HuggingFaceVoice> voices = new ArrayList<HuggingFaceVoice>();
        for (ModelSummary repository : repositories.values()) {
            List<HubFile> files = hub.models().model(repository.getId()).files().execute();
            voices.addAll(voices(repository.getId(), paths(files)));
        }
        return voices;
    }

    private ModelSearchRequest request(String query) {
        String text = query == null ? "" : query.trim();
        ModelSearchRequest request = text.isEmpty() ? hub.models().search() : hub.models().search(text);
        return request.sortByDownloads().limit(LIMIT);
    }

    private static void collect(Map<String, ModelSummary> into, ModelSearchRequest request, int limit)
            throws HuggingFaceHubException {
        for (ModelSummary summary : request.execute().getModels()) {
            if (summary.getId() != null && !Boolean.TRUE.equals(summary.getGated())
                    && into.size() < limit && !into.containsKey(summary.getId())) {
                into.put(summary.getId(), summary);
            }
        }
    }

    private static Set<String> paths(List<HubFile> files) {
        Set<String> paths = new LinkedHashSet<String>();
        for (HubFile file : files) {
            if (file.getPath() != null) {
                paths.add(file.getPath());
            }
        }
        return paths;
    }

    /** Die Stimmen eines Repositorys nach seinen Dateien; leer, wenn keine Form passt. */
    static List<HuggingFaceVoice> voices(String repository, Set<String> paths) {
        List<HuggingFaceVoice> voices = new ArrayList<HuggingFaceVoice>();
        for (String path : paths) {
            if (!path.endsWith(".onnx") || !paths.contains(path + ".json")) {
                continue;
            }
            String base = HuggingFaceVoice.fileName(path);
            String name = base.substring(0, base.length() - ".onnx".length());
            Matcher piper = PIPER_NAME.matcher(name);
            String language = "";
            if (piper.matches()) {
                if (!"de".equals(piper.group(1))) {
                    continue;
                }
                language = name.substring(0, name.indexOf('-'));
            }
            String id = directoryName(name);
            if (id != null) {
                voices.add(new HuggingFaceVoice(id, name + " · Piper · " + repository, language, repository, null,
                        Arrays.asList(path, path + ".json")));
            }
        }
        String graph = paths.contains("onnx/model.onnx") ? "onnx/model.onnx"
                : paths.contains("model.onnx") ? "model.onnx" : null;
        if (voices.isEmpty() && graph != null && paths.contains("config.json") && paths.contains("vocab.json")) {
            List<String> files = new ArrayList<String>();
            files.add("config.json");
            files.add("vocab.json");
            if (paths.contains("tokenizer_config.json")) {
                files.add("tokenizer_config.json");
            }
            files.add(graph);
            String id = directoryName(repository.replace("/", "--"));
            if (id != null) {
                voices.add(new HuggingFaceVoice(id, repository + " · VITS", "", repository, null, files));
            }
        }
        return Collections.unmodifiableList(voices);
    }

    /**
     * Ein Embedding- oder Chat-Modell aus den Dateien eines Repositorys; {@code null}, wenn Konfiguration, Tokenizer
     * oder ein ladbarer Graph fehlen. Embeddings mit eigener Dense-Schicht ({@code 2_Dense}) fehlen: die steckt
     * nicht im ONNX-Graphen.
     */
    static HuggingFaceVoice textModel(String repository, Set<String> paths, ModelCategory category) {
        if (!paths.contains("config.json") || !paths.contains("tokenizer.json")) {
            return null;
        }
        if (category == ModelCategory.EMBEDDING && paths.contains("2_Dense/config.json")) {
            return null;
        }
        String graph = null;
        for (String candidate : category == ModelCategory.CHAT ? CHAT_GRAPHS : EMBEDDING_GRAPHS) {
            for (String directory : new String[] {"onnx/", ""}) {
                if (graph == null && paths.contains(directory + candidate)) {
                    graph = directory + candidate;
                }
            }
        }
        if (graph == null) {
            return null;
        }
        List<String> files = new ArrayList<String>();
        files.add("config.json");
        files.add("tokenizer.json");
        for (String extra : TEXT_EXTRAS) {
            if (paths.contains(extra)) {
                files.add(extra);
            }
        }
        files.add(graph);
        for (String path : paths) {
            if (path.startsWith(graph + "_data")) {
                files.add(path);
            }
        }
        String variant = HuggingFaceVoice.fileName(graph);
        String name = repository.replace("/", "--") + ("model.onnx".equals(variant) ? ""
                : "--" + variant.substring(0, variant.length() - ".onnx".length()));
        String id = directoryName(name);
        if (id == null) {
            return null;
        }
        String label = category == ModelCategory.CHAT ? "Chat" : "Embedding";
        return new HuggingFaceVoice(id, label + " · " + repository + " (" + variant + ")", "", repository, null,
                files, category);
    }

    /** Ordnername unter dem Modellverzeichnis; {@code null}, wenn der Name keiner sein kann. */
    private static String directoryName(String name) {
        String id = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return id.isEmpty() || id.startsWith(".") ? null : id;
    }
}
