/**
 * Vorlesen von Antworten (aus askai-java8 arch {@code PiperReadAloudService}, ohne piper.exe, Windows-Stimme und
 * NLP-Sprachtrennung): {@link com.aresstack.enterpriseai.application.speech.ReadAloudService} zerlegt den Text in
 * Absätze und Sätze, lässt sie über den {@link com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort} vorab
 * synthetisieren und spielt sie strikt in Lesereihenfolge über {@link
 * com.aresstack.enterpriseai.application.speech.AudioPlayback} ab; Stopp bricht nach dem laufenden Stück ab.
 * Spracheingabe: {@link com.aresstack.enterpriseai.application.speech.DictationService} macht aus einer Aufnahme
 * Text über den {@link com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort} des Katalogs von {@code model.stt}.
 */
package com.aresstack.enterpriseai.application.speech;
