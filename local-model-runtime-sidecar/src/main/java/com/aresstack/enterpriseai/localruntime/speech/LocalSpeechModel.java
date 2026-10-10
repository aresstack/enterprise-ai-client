package com.aresstack.enterpriseai.localruntime.speech;

import java.nio.file.Path;
import java.util.Map;

/**
 * One locally installed text-to-speech voice: a VITS model exported to ONNX in the Hugging Face layout
 * ({@code config.json} with {@code "model_type": "vits"}, {@code vocab.json}, optional
 * {@code tokenizer_config.json}, {@code onnx/model.onnx} or {@code model.onnx}). The virtual name is the
 * directory name, exactly what {@code /api/tags} lists and {@code /v1/audio/speech} expects as {@code model}.
 *
 * @param virtualName  directory name below the model root
 * @param directory    the model directory
 * @param onnxFile     the ONNX graph
 * @param sampleRate   {@code sampling_rate} from {@code config.json} (default 16000)
 * @param vocabulary   character to token id ({@code vocab.json})
 * @param addBlank     intersperse token id 0 between characters ({@code add_blank}, default true)
 * @param lowerCase    lower-case characters that are not in the vocabulary ({@code normalize}, default true)
 */
public record LocalSpeechModel(String virtualName, Path directory, Path onnxFile, int sampleRate,
                               Map<String, Integer> vocabulary, boolean addBlank, boolean lowerCase) {
}
