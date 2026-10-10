/**
 * Versorgung des lokalen Sidecars mit Stimmen für die Sprachausgabe über {@code com.aresstack:huggingface4j}:
 * kuratierte Stimmen aus der Konfiguration ({@link com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice}),
 * Download mit Fortsetzen und SHA-256-Prüfung in einen Zwischenordner, dann Verschieben nach
 * {@code <Modellverzeichnis>/<id>}, wo der Sidecar sie liest. Die Proxy-Route kommt je Ziel aus dem Port
 * {@code http-api}; einen eigenen Hugging-Face-Client gibt es hier nicht.
 */
package com.aresstack.enterpriseai.model.huggingface;
