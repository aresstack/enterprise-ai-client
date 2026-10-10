/**
 * Neutraler Port der Inferenz-Laufzeit für lokale Stimmen: {@link com.aresstack.enterpriseai.speech.runtime.LocalSpeechRuntime}
 * macht aus den Token-Ids einer Stimme Audio-Samples. Wer den Graphen rechnet, ist Sache des Adapters; heute der
 * Pilot {@code speech-onnx} (ONNX Runtime im Prozess), später eine eigene Inferenz-Engine. Keine ONNX-Typen hier.
 */
package com.aresstack.enterpriseai.speech.runtime;
