package com.aresstack.enterpriseai.document.tika;

import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;

/**
 * Die Standardausstattung für die Composition Root: Erkennung per Endung mit Tika-Rückfall und die Extraktoren in
 * fester Reihenfolge (Markdown und Klartext ohne Tika, alles andere über Tika).
 */
public final class DocumentExtraction {

    private DocumentExtraction() {
    }

    public static ContentDetector detector() {
        return new TikaContentDetector();
    }

    public static ExtractionRegistry registry() {
        ExtractionRegistry registry = new ExtractionRegistry();
        registry.register(new MarkdownTextExtractor());
        registry.register(new PlainTextExtractor());
        registry.register(new TikaResourceExtractor());
        return registry;
    }
}
