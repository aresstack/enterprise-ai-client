/**
 * Modellverwaltung nach Kategorien (wie {@code AiModelSelections} in askai-java8 arch, ohne Ollama-,
 * HuggingFace- und Installer-Unterbau): {@link com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory}
 * nennt die Funktion, {@link com.aresstack.enterpriseai.astu.modelcatalog.ModelDescriptor} ein Modell eines
 * Katalogs mit seinen gemeldeten Fähigkeiten, {@link com.aresstack.enterpriseai.astu.modelcatalog.ModelSelections}
 * die eine Auswahl je Kategorie. Welcher Katalog ein Modell liefert (entfernt oder lokal), ist für die Auswahl
 * gleichgültig; der Endpunkt wird erst dahinter aufgelöst.
 */
package com.aresstack.enterpriseai.astu.modelcatalog;
