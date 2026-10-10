package com.aresstack.enterpriseai.text.onnx;

import com.aresstack.enterpriseai.text.runtime.CausalLmRuntime;
import com.aresstack.enterpriseai.text.runtime.DecoderSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.nio.file.Path;

/**
 * {@link CausalLmRuntime} mit ONNX Runtime für Decoder-Exporte mit KV-Cache (wie sie Optimum und transformers.js
 * erzeugen): {@code input_ids}, {@code attention_mask}, optional {@code position_ids}, {@code past_key_values.<n>.key}
 * und {@code .value} hinein, {@code logits} und {@code present.<n>.key}/{@code .value} heraus. Kein onnxruntime-genai:
 * die reine ONNX-Runtime-Bibliothek läuft auf Java 8, die Schleife steht in {@link OnnxDecoderSession}.
 */
public final class OnnxCausalLmRuntime implements CausalLmRuntime {

    private final OnnxSessions sessions = new OnnxSessions();

    @Override
    public String name() {
        return "onnxruntime";
    }

    @Override
    public DecoderSession open(Path modelFile) throws TextRuntimeException {
        return new OnnxDecoderSession(modelFile.getFileName().toString(), sessions.environment(),
                sessions.session(modelFile));
    }

    @Override
    public void close() {
        sessions.close();
    }
}
