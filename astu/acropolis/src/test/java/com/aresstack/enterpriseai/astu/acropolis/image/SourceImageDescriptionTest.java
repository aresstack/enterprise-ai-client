package com.aresstack.enterpriseai.astu.acropolis.image;

import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Beschreibung auf Abruf: genau ein Vision-Aufruf je Bild und Version, danach aus dem Cache; ohne Modell keiner. */
public class SourceImageDescriptionTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, 0};

    private final Map<String, ImageAnalysis> stored = new HashMap<String, ImageAnalysis>();
    private final ImageAnalysisCache cache = new ImageAnalysisCache() {
        @Override
        public ImageAnalysis get(String cacheKey) {
            return stored.get(cacheKey);
        }

        @Override
        public void put(String cacheKey, ImageAnalysis analysis) {
            stored.put(cacheKey, analysis);
        }
    };
    private final Supplier<Collection<KnowledgeSourcePort>> noSources =
            () -> Collections.<KnowledgeSourcePort>emptyList();

    @Test
    public void describesOnceAndAnswersTheSecondTimeFromTheCache() {
        FakeChatCompletionPort chat = new FakeChatCompletionPort().enqueueAnswer("Ein Balkendiagramm.");
        SourceImageAnalysisUseCase useCase = new SourceImageAnalysisUseCase(noSources, chat, () -> "ocr-model",
                () -> "vision-model", cache);

        ImageAnalysis first = useCase.describe("confluence:host/attachment/att1", "Umsatz", "3", "image/png", PNG);
        ImageAnalysis second = useCase.describe("confluence:host/attachment/att1", "Umsatz", "3", "image/png", PNG);

        assertEquals("Ein Balkendiagramm.", first.description());
        assertEquals("", first.ocrText()); // OCR bleibt getrennt: beim Umschalten nur die Beschreibung
        assertEquals("Ein Balkendiagramm.", second.description());
        assertEquals(1, chat.requests().size());
        assertEquals("vision-model", chat.lastRequest().options().model());
    }

    @Test
    public void aNewVersionIsDescribedAgain() {
        FakeChatCompletionPort chat = new FakeChatCompletionPort().enqueueAnswer("alt").enqueueAnswer("neu");
        SourceImageAnalysisUseCase useCase = new SourceImageAnalysisUseCase(noSources, chat, () -> null,
                () -> "vision-model", cache);

        useCase.describe("img", "", "1", "image/png", PNG);
        ImageAnalysis newer = useCase.describe("img", "", "2", "image/png", PNG);

        assertEquals("neu", newer.description());
        assertEquals(2, chat.requests().size());
    }

    @Test
    public void withoutAVisionModelNothingIsAsked() {
        FakeChatCompletionPort chat = new FakeChatCompletionPort();
        SourceImageAnalysisUseCase useCase = new SourceImageAnalysisUseCase(noSources, chat, () -> "ocr-model",
                () -> " ", cache);

        assertNull(useCase.describe("img", "", "1", "image/png", PNG));
        assertEquals(0, chat.requests().size());
    }
}
