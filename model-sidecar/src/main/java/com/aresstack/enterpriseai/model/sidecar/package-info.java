/**
 * Optionaler lokaler Modellkatalog: startet den Java-21-Sidecar {@code local-model-runtime-sidecar-java21} aus
 * askai-java8 arch ({@code --host=127.0.0.1 --port=0 --model-root=<pfad> --backend=cpu}), liest dessen eine
 * Bereitschaftszeile {@code {"event":"ready","baseUrl":...}} und fragt {@code GET /api/tags} ab. Ohne Installer,
 * ohne HuggingFace- und Ollama-Unterbau: ist kein Java 21 konfiguriert, entsteht dieser Adapter gar nicht und die
 * lokalen Einträge fehlen einfach.
 */
package com.aresstack.enterpriseai.model.sidecar;
