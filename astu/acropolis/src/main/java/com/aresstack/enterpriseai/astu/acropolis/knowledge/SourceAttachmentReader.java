package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceFile;

/**
 * Liest Dateianhänge einer Quelle (PDF, Office, Bilder) als Wissensdokument: Datei über
 * {@link PageViewSource#loadAttachmentFile}, Text über {@link SourceFileTextExtractor}. Das Dokument trägt die
 * Ressource der Quelle (Seite als Eltern, Revision, Adresse) und geht wie eine Seite in Roh- und Wissensindex; die
 * Bytes werden danach verworfen. Alles andere liest {@code delegate}.
 *
 * <p>Verlangt die Quelle einen Einmalcode, geht {@link com.aresstack.enterpriseai.source.api.OneTimeCodeRequiredException}
 * unverändert an den Aufrufer.</p>
 */
public final class SourceAttachmentReader implements KnowledgeDocumentReader {

    private final KnowledgeDocumentReader delegate;
    private final SourceFileTextExtractor extractor;

    public SourceAttachmentReader(KnowledgeDocumentReader delegate, SourceFileTextExtractor extractor) {
        if (delegate == null || extractor == null) {
            throw new IllegalArgumentException("delegate und extractor sind Pflicht");
        }
        this.delegate = delegate;
        this.extractor = extractor;
    }

    @Override
    public KnowledgeDocument read(SourceAddress owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException {
        if (isAttachment(source, id)) {
            return file((PageViewSource) source, id);
        }
        try {
            return delegate.read(owner, source, id);
        } catch (KnowledgeSourceException e) {
            return fallback(source, id, e);
        }
    }

    @Override
    public KnowledgeDocument refresh(SourceAddress owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException {
        if (isAttachment(source, id)) {
            return file((PageViewSource) source, id);
        }
        try {
            return delegate.refresh(owner, source, id);
        } catch (KnowledgeSourceException e) {
            return fallback(source, id, e);
        }
    }

    private static boolean isAttachment(KnowledgeSourcePort source, KnowledgeResourceId id) {
        return source instanceof PageViewSource && ((PageViewSource) source).isAttachment(id);
    }

    /** Try the file path when the text path cannot read the id (a source not yet opened knows no attachments). */
    private KnowledgeDocument fallback(KnowledgeSourcePort source, KnowledgeResourceId id,
                                       KnowledgeSourceException failure) throws KnowledgeSourceException {
        if (failure.kind() != KnowledgeSourceException.Kind.UNSUPPORTED || !(source instanceof PageViewSource)) {
            throw failure;
        }
        try {
            return file((PageViewSource) source, id);
        } catch (KnowledgeSourceException again) {
            throw again.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? failure : again;
        }
    }

    private KnowledgeDocument file(PageViewSource source, KnowledgeResourceId id) throws KnowledgeSourceException {
        SourceFile file = source.loadAttachmentFile(id);
        String text = extractor.extract(file.resource(), file.content());
        String title = file.resource().title();
        String body = text == null ? "" : text.trim();
        return KnowledgeDocument.of(file.resource(), body.isEmpty() ? "# " + title : "# " + title + "\n\n" + body);
    }
}
