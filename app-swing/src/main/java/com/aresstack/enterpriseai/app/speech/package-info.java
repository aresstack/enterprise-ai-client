/**
 * Sprachausgabe in der Composition Root: {@link com.aresstack.enterpriseai.app.speech.SpeechOutput} entscheidet aus
 * der Konfiguration (TTS-Auswahl, lokaler Sidecar), ob vorgelesen werden kann, {@link
 * com.aresstack.enterpriseai.app.speech.ReadAloudBinding} verbindet den Lautsprecher-Knopf des Verlaufs mit
 * {@code application.speech.ReadAloudService}, {@link com.aresstack.enterpriseai.app.speech.JavaSoundAudioPlayback}
 * spielt das WAV-Audio über Java Sound ab.
 */
package com.aresstack.enterpriseai.app.speech;
