/**
 * Pilot-Backend der lokalen Sprachausgabe: {@link com.aresstack.enterpriseai.speech.onnx.OnnxSpeechRuntime} rechnet
 * Piper- und VITS-Graphen mit ONNX Runtime (CPU, im Prozess). Alles ONNX-Spezifische bleibt in diesem Modul; eine
 * eigene Inferenz-Engine ersetzt es hinter {@code speech-runtime-api}.
 */
package com.aresstack.enterpriseai.speech.onnx;
