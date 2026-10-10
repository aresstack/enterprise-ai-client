/**
 * Sprache in der Composition Root: {@link com.aresstack.enterpriseai.app.speech.SpeechOutput} entscheidet aus
 * der TTS-Auswahl und den Sprachausgaben je Modellquelle, ob und womit vorgelesen wird, {@link
 * com.aresstack.enterpriseai.app.speech.ReadAloudBinding} verbindet den Play/Pause-Orb des Verlaufs mit
 * {@code application.speech.ReadAloudService}, {@link com.aresstack.enterpriseai.app.speech.JavaSoundAudioPlayback}
 * spielt das WAV-Audio über Java Sound ab. {@link com.aresstack.enterpriseai.app.speech.Dictation} verbindet den
 * Mikrofon-Knopf des Composers mit {@code application.speech.DictationService} (STT-Auswahl, Aufnahme über Java
 * Sound).
 */
package com.aresstack.enterpriseai.app.speech;
