package com.aresstack.enterpriseai.application.speech;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Sprachausgabe je Modellkatalog: die Auswahl {@code model.tts} nennt Katalog und Modell, die Registry liefert den
 * {@link SpeechSynthesisPort} dieses Katalogs. Wer vorliest, kennt keinen konkreten Anbieter (Enterprise-API oder
 * lokaler Sidecar); welche Kataloge sprechen können, entscheidet allein die Composition Root.
 */
public final class SpeechSynthesisRegistry {

    private final Map<String, SpeechSynthesisPort> ports;

    /** @param ports je Katalog höchstens ein Port ({@link SpeechSynthesisPort#catalogId()}) */
    public SpeechSynthesisRegistry(List<SpeechSynthesisPort> ports) {
        Map<String, SpeechSynthesisPort> byCatalog = new LinkedHashMap<String, SpeechSynthesisPort>();
        if (ports != null) {
            for (SpeechSynthesisPort port : ports) {
                if (byCatalog.put(port.catalogId(), port) != null) {
                    throw new IllegalArgumentException("duplicate speech port for catalog " + port.catalogId());
                }
            }
        }
        this.ports = Collections.unmodifiableMap(byCatalog);
    }

    /** Ob dieser Katalog sprechen kann. */
    public boolean supports(String catalogId) {
        return ports.containsKey(catalogId);
    }

    /** Der Port des Katalogs; {@link IllegalStateException}, wenn der Katalog nicht sprechen kann. */
    public SpeechSynthesisPort require(String catalogId) {
        SpeechSynthesisPort port = ports.get(catalogId);
        if (port == null) {
            throw new IllegalStateException("no speech synthesis for catalog " + catalogId);
        }
        return port;
    }

    @Override
    public String toString() {
        return "SpeechSynthesisRegistry" + ports.keySet();
    }
}
