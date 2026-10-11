/**
 * Neutrale Ports der Sprache: {@link com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort} macht aus Text
 * WAV-Audio mit einem Modell aus der Kategorie TTS des Modellkatalogs, {@link
 * com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort} aus einer Aufnahme Text mit einem Modell aus der
 * Kategorie STT. Sie implementieren die lokalen Modelle im Client ({@code speech-local}, {@code text-local}) und der
 * Adapter für {@code /audio/speech} der Enterprise-API. {@link com.aresstack.enterpriseai.speech.api.TextAnalysisEngine} erkennt Sätze und Sprache
 * beim Vorlesen (Apache OpenNLP in {@code speech-opennlp}). Abspielen, Vorlese-Ablauf und Auswahl je Katalog liegen in
 * {@code application.speech}.
 */
package com.aresstack.enterpriseai.speech.api;
