/**
 * Port der Inferenz-Laufzeit lokaler Text-Modelle: {@link com.aresstack.enterpriseai.text.runtime.TextEncoderRuntime}
 * rechnet Encoder-Graphen für Embeddings, {@link com.aresstack.enterpriseai.text.runtime.CausalLmRuntime} Decoder für
 * Chat (Token für Token mit Zwischenspeicher). Tokenizer, Pooling, Chat-Vorlage und Generierung kennen nur diese
 * Interfaces; der Pilot ist ONNX Runtime, eine eigene Inferenz-Engine ersetzt ihn hier.
 */
package com.aresstack.enterpriseai.text.runtime;
