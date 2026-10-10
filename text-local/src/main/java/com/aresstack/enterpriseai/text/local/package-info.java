/**
 * Lokale Text-Modelle im Java-8-Client: {@link com.aresstack.enterpriseai.text.local.LocalEmbeddingAdapter} liest ein
 * installiertes Embedding-Modell (ONNX-Export vom Hugging Face Hub) aus dem Modellverzeichnis, tokenisiert mit dem
 * Tokenizer aus {@code tokenizer.json} in reinem Java (WordPiece, BPE, Unigram) und lässt den Graphen von
 * {@link com.aresstack.enterpriseai.text.runtime.TextEncoderRuntime} rechnen (Pilot: ONNX Runtime).
 * {@link com.aresstack.enterpriseai.text.local.LocalSpeechRecognitionAdapter} erkennt Sprache mit einem Gemma-Modell
 * mit Audio-Eingang (Log-Mel-Merkmale in reinem Java, Rechnung über
 * {@link com.aresstack.enterpriseai.text.runtime.AudioLanguageModelRuntime}). Kein externes Programm, keine native
 * Tokenizer-Bibliothek.
 */
package com.aresstack.enterpriseai.text.local;
