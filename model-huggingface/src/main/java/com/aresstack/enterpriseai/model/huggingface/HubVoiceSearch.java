package com.aresstack.enterpriseai.model.huggingface;

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
 * Sucht auf dem Hugging Face Hub nach Sprachausgabe-Modellen, die der Sidecar ohne Umwandlung lädt, und macht
 * aus jedem Treffer die installierbaren Stimmen:
 * <ul>
 *   <li>Piper: je Paar {@code <name>.onnx} + {@code <name>.onnx.json} eine Stimme. Folgt der Name dem Piper-Schema
 *   {@code <sprache>_<REGION>-…}, bleiben nur deutsche: andere espeak-Sprachen kann der Sidecar nicht
 *   phonemisieren. Ob die Stimme wirklich passt, prüft die Installation an der {@code .onnx.json}.</li>
 *   <li>VITS im Hugging-Face-Layout: {@code config.json}, {@code vocab.json} und {@code onnx/model.onnx} oder
 *   {@code model.onnx}; {@code model_type=vits} prüft die Installation an der {@code config.json}.</li>
 * </ul>
 * Chat- und Embedding-Modelle bietet die Suche nicht an: der Sidecar hat dafür noch keine Laufzeit, die
 * Hub-Dateien direkt lädt.
 */
final class HubVoiceSearch {

    /** Treffer je Hub-Anfrage; die Dateiliste wird nur für diese gelesen. */
    static final int LIMIT = 20;
    private static final Pattern PIPER_NAME = Pattern.compile("^([a-z]{2,3})_[A-Z]{2}-.+");

    private final HuggingFaceHub hub;

    HubVoiceSearch(HuggingFaceHub hub) {
        this.hub = hub;
    }

    /** Unterstützte Stimmen zur Suche; leer, wenn nichts passt. Blockiert (Netz). */
    List<HuggingFaceVoice> search(String query) throws HuggingFaceHubException {
        Map<String, ModelSummary> repositories = new LinkedHashMap<String, ModelSummary>();
        collect(repositories, request(query).task("text-to-speech"));
        collect(repositories, request(query).task("text-to-audio").filter("onnx"));
        collect(repositories, request(query).filter("onnx"));
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

    private static void collect(Map<String, ModelSummary> into, ModelSearchRequest request)
            throws HuggingFaceHubException {
        for (ModelSummary summary : request.execute().getModels()) {
            if (summary.getId() != null && !Boolean.TRUE.equals(summary.getGated())
                    && into.size() < LIMIT * 2 && !into.containsKey(summary.getId())) {
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

    /** Ordnername unter dem Modellverzeichnis; {@code null}, wenn der Name keiner sein kann. */
    private static String directoryName(String name) {
        String id = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return id.isEmpty() || id.startsWith(".") ? null : id;
    }
}
