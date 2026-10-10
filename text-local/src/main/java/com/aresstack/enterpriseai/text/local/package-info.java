/**
 * Lokale Text-Modelle im Java-8-Client: {@link com.aresstack.enterpriseai.text.local.LocalEmbeddingAdapter} liest ein
 * installiertes Embedding-Modell (ONNX-Export vom Hugging Face Hub) aus dem Modellverzeichnis, tokenisiert mit dem
 * Tokenizer aus {@code tokenizer.json} in reinem Java (WordPiece, BPE, Unigram) und lässt den Graphen von
 * {@link com.aresstack.enterpriseai.text.runtime.TextEncoderRuntime} rechnen (Pilot: ONNX Runtime). Kein externes
 * Programm, keine native Tokenizer-Bibliothek.
 */
package com.aresstack.enterpriseai.text.local;
