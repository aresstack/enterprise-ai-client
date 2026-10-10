/**
 * Lokale Sprachausgabe im Java-8-Client: {@link com.aresstack.enterpriseai.speech.local.LocalVoiceSpeechAdapter}
 * liest installierte Stimmen aus dem Modellverzeichnis (Piper {@code .onnx} + {@code .onnx.json} mit
 * regelbasierter deutscher Aussprache, VITS im Hugging-Face-Layout), macht aus Text Token-Ids und lässt den Graphen
 * von {@link com.aresstack.enterpriseai.speech.runtime.LocalSpeechRuntime} rechnen (Pilot: ONNX Runtime). Kein externes
 * Programm.
 */
package com.aresstack.enterpriseai.speech.local;
