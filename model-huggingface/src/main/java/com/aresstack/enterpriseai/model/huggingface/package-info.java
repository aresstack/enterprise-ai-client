/**
 * Versorgung des Clients mit lokalen Modellen über {@code com.aresstack:huggingface4j}: kuratierte Stimmen aus der
 * Konfiguration ({@link com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice}) und Treffer der Hub-Suche
 * (Stimmen, Embedding- und Chat-Modelle, nur ohne Umwandlung ladbare Formate), Download mit Fortsetzen und
 * SHA-256-Prüfung in einen Zwischenordner, dann Verschieben nach {@code <Modellverzeichnis>/<id>}, wo speech-local
 * und text-local sie lesen. Die Proxy-Route kommt je Ziel aus dem Port {@code http-api}; einen eigenen
 * Hugging-Face-Client gibt es hier nicht.
 */
package com.aresstack.enterpriseai.model.huggingface;
