package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.application.attachment.AttachmentException;
import com.aresstack.enterpriseai.application.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.DetectedContentType;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;
import com.aresstack.enterpriseai.document.api.ExtractionRequest;
import com.aresstack.enterpriseai.document.api.ExtractionResult;
import com.aresstack.enterpriseai.document.api.ResourceExtractor;

import java.util.Arrays;

/**
 * Textextraktion für Anhänge über den Dokument-Port (document-api): dieselbe Erkennung und dieselben Extraktoren
 * wie die Wissensquelle „Lokale Dateien“ (Markdown und Klartext direkt, alles andere über Apache Tika).
 */
public final class DocumentAttachmentTextExtractor implements AttachmentTextExtractor {

    private static final int PREFIX_BYTES = 8192;

    private final ContentDetector detector;
    private final ExtractionRegistry extractors;

    public DocumentAttachmentTextExtractor(ContentDetector detector, ExtractionRegistry extractors) {
        if (detector == null || extractors == null) {
            throw new IllegalArgumentException("detector and extractors must not be null");
        }
        this.detector = detector;
        this.extractors = extractors;
    }

    @Override
    public String extractText(String fileName, byte[] content) {
        DetectedContentType type = detector.detect(fileName, null,
                Arrays.copyOf(content, Math.min(content.length, PREFIX_BYTES)));
        ResourceExtractor extractor = extractors.findExtractor(type);
        if (extractor == null) {
            throw new AttachmentException(fileName + ": Format " + type.mimeType() + " wird nicht unterstützt");
        }
        ExtractionResult result;
        try {
            result = extractor.extract(new ExtractionRequest(fileName, content, fileName, null, type));
        } catch (RuntimeException e) {
            throw new AttachmentException(fileName + ": Text konnte nicht gelesen werden ("
                    + e.getClass().getSimpleName() + ")", e);
        }
        if (!result.isSuccess()) {
            throw new AttachmentException(fileName + ": " + result.errorMessage());
        }
        return result.document().combinedText();
    }
}
