/**
 * Ausführung der Modellauswahl je Kategorie: die {@link com.aresstack.enterpriseai.domain.modelcatalog.ModelReference}
 * (Katalog + Modell) entscheidet, welcher Adapter eine Anfrage bedient. {@link
 * com.aresstack.enterpriseai.application.modelexecution.ChatModelExecutorRegistry} routet Chat und Werkzeug-Chat,
 * {@link com.aresstack.enterpriseai.application.modelexecution.EmbeddingModelExecutorRegistry} wählt den
 * Embedding-Port. Welche Kataloge welche Kategorie ausführen können, legt allein die Composition Root fest; hier gibt
 * es keine Sonderlogik für einen bestimmten Anbieter.
 */
package com.aresstack.enterpriseai.application.modelexecution;
