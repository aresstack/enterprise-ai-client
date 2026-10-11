package com.aresstack.enterpriseai.astu.acropolis.speech;

import com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Spracheingabe je Modellkatalog: die Auswahl {@code model.stt} nennt Katalog und Modell, die Registry liefert den
 * {@link SpeechRecognitionPort} dieses Katalogs. Wer diktiert, kennt keinen konkreten Anbieter (Enterprise-API oder
 * lokales Modell im Client); welche Kataloge Sprache erkennen, entscheidet allein die Composition Root.
 */
public final class SpeechRecognitionRegistry {

    private final Map<String, SpeechRecognitionPort> ports;

    /** @param ports je Katalog höchstens ein Port ({@link SpeechRecognitionPort#catalogId()}) */
    public SpeechRecognitionRegistry(List<SpeechRecognitionPort> ports) {
        Map<String, SpeechRecognitionPort> byCatalog = new LinkedHashMap<String, SpeechRecognitionPort>();
        if (ports != null) {
            for (SpeechRecognitionPort port : ports) {
                if (byCatalog.put(port.catalogId(), port) != null) {
                    throw new IllegalArgumentException("duplicate speech recognition port for catalog " + port.catalogId());
                }
            }
        }
        this.ports = Collections.unmodifiableMap(byCatalog);
    }

    /** Ob dieser Katalog Sprache erkennen kann. */
    public boolean supports(String catalogId) {
        return ports.containsKey(catalogId);
    }

    /** Der Port des Katalogs; {@link IllegalStateException}, wenn der Katalog keine Sprache erkennt. */
    public SpeechRecognitionPort require(String catalogId) {
        SpeechRecognitionPort port = ports.get(catalogId);
        if (port == null) {
            throw new IllegalStateException("no speech recognition for catalog " + catalogId);
        }
        return port;
    }

    @Override
    public String toString() {
        return "SpeechRecognitionRegistry" + ports.keySet();
    }
}
