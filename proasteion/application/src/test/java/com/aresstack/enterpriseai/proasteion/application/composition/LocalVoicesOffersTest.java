package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Eine frische Installation (leeres oder fehlendes Modellverzeichnis) bekommt mit der ausgelieferten Konfiguration
 * den Gemma-Vorschlag für die Spracheingabe als installierbares Angebot; der Einrichtungsassistent zeigt dann
 * „Installieren“ statt eines leeren Felds.
 */
public class LocalVoicesOffersTest {

    @Test
    public void freshInstallationOffersTheSpeechInputSuggestion() throws Exception {
        Path empty = Files.createTempDirectory("local-models");
        try {
            assertSpeechSuggestion(new LocalVoices(new RefusingPrompts()).offers(empty));
            assertSpeechSuggestion(new LocalVoices(new RefusingPrompts()).offers(empty.resolve("missing")));
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    private static void assertSpeechSuggestion(List<LocalVoiceOffer> offers) {
        LocalVoiceOffer speech = null;
        for (LocalVoiceOffer offer : offers) {
            if (offer.category() == ModelCategory.STT) {
                speech = offer;
            }
        }
        assertTrue("STT-Vorschlag fehlt: " + offers, speech != null);
        assertFalse("noch nicht installiert", speech.isInstalled());
        assertTrue(speech.displayName(), speech.displayName().contains("Gemma 4 E2B"));
    }
}
