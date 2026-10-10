package com.aresstack.enterpriseai.text.onnx;

import com.aresstack.enterpriseai.text.runtime.AudioLanguageModelRuntime;
import com.aresstack.enterpriseai.text.runtime.AudioModelGraphs;
import com.aresstack.enterpriseai.text.runtime.AudioPromptSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

/**
 * {@link AudioLanguageModelRuntime} mit ONNX Runtime für Exporte im Layout von transformers.js (Gemma 4):
 * {@code audio_encoder} ({@code input_features}, {@code input_features_mask} hinein, {@code audio_features} heraus),
 * {@code embed_tokens} ({@code input_ids} hinein, {@code inputs_embeds} und {@code per_layer_inputs} heraus) und
 * {@code decoder_model_merged} mit KV-Cache. Die Schleife steht in {@link OnnxAudioPromptSession}; kein
 * onnxruntime-genai, die reine ONNX-Runtime-Bibliothek läuft auf Java 8.
 */
public final class OnnxAudioLanguageModelRuntime implements AudioLanguageModelRuntime {

    private final OnnxSessions sessions = new OnnxSessions();

    @Override
    public String name() {
        return "onnxruntime";
    }

    @Override
    public AudioPromptSession open(AudioModelGraphs graphs) throws TextRuntimeException {
        return new OnnxAudioPromptSession(sessions.environment(), sessions.session(graphs.audioEncoder()),
                sessions.session(graphs.embedTokens()), sessions.session(graphs.decoder()),
                graphs.decoder().getFileName().toString());
    }

    @Override
    public void close() {
        sessions.close();
    }
}
