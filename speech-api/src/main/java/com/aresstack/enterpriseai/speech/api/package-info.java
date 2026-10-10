/**
 * Neutraler Port der Sprachausgabe: {@link com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort} macht aus
 * Text WAV-Audio mit einem Modell aus der Kategorie TTS des Modellkatalogs. Heute implementiert ihn nur der
 * optionale lokale Java-21-Sidecar ({@code model-sidecar}); ein Adapter für {@code /audio/speech} der Enterprise-API
 * kann später daneben treten. Abspielen und Vorlese-Ablauf liegen in {@code application.speech}.
 */
package com.aresstack.enterpriseai.speech.api;
