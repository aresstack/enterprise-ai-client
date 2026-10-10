/**
 * Vorlesen von Antworten (aus askai-java8 arch {@code PiperReadAloudService}, ohne piper.exe, Windows-Stimme und
 * NLP-Sprachtrennung): {@link com.aresstack.enterpriseai.application.speech.ReadAloudService} zerlegt den Text in
 * Absätze und Sätze, lässt sie über den {@link com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort} vorab
 * synthetisieren und spielt sie strikt in Lesereihenfolge über {@link
 * com.aresstack.enterpriseai.application.speech.AudioPlayback} ab; Stopp bricht nach dem laufenden Stück ab.
 */
package com.aresstack.enterpriseai.application.speech;
