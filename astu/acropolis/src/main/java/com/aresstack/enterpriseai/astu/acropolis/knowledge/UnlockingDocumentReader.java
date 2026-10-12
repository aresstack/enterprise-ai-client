package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.OneTimeCodeRequiredException;

import java.util.function.Supplier;

/**
 * Lesen auf Anforderung (etwa {@code knowledge_read}) mit Einmalcode: verlangt die Quelle einen Code, wartet der
 * Lesezugriff, bis der Benutzer ihn über {@link OneTimeCodePrompt} eingegeben hat, und liest dann einmal neu.
 * Abfragen laufen nacheinander; wer wartet, liest nach einer Freischaltung zuerst neu und fragt nur, wenn die Quelle
 * weiterhin einen Code verlangt. Das Vorladen im Hintergrund nutzt diesen Leser nicht und fragt deshalb nie.
 */
public final class UnlockingDocumentReader implements KnowledgeDocumentReader {

    private final KnowledgeDocumentReader delegate;
    private final Supplier<OneTimeCodePrompt> prompt;
    private final Object promptLock = new Object();

    /** @param prompt die aktuelle Oberfläche je Aufruf (spät gebunden; {@code null} heißt keine) */
    public UnlockingDocumentReader(KnowledgeDocumentReader delegate, Supplier<OneTimeCodePrompt> prompt) {
        if (delegate == null || prompt == null) {
            throw new IllegalArgumentException("delegate und prompt sind Pflicht");
        }
        this.delegate = delegate;
        this.prompt = prompt;
    }

    @Override
    public KnowledgeDocument read(final SourceAddress owner, final KnowledgeSourcePort source,
                                  final KnowledgeResourceId id) throws KnowledgeSourceException {
        return unlocked(owner, () -> delegate.read(owner, source, id));
    }

    @Override
    public KnowledgeDocument refresh(final SourceAddress owner, final KnowledgeSourcePort source,
                                     final KnowledgeResourceId id) throws KnowledgeSourceException {
        return unlocked(owner, () -> delegate.refresh(owner, source, id));
    }

    private KnowledgeDocument unlocked(SourceAddress owner, Read read) throws KnowledgeSourceException {
        try {
            return read.run();
        } catch (OneTimeCodeRequiredException first) {
            synchronized (promptLock) {
                OneTimeCodeRequiredException required;
                try {
                    return read.run(); // another caller may have unlocked the source meanwhile
                } catch (OneTimeCodeRequiredException still) {
                    required = still;
                }
                OneTimeCodePrompt current = prompt.get();
                OneTimeCodePrompt.Outcome outcome = current == null ? OneTimeCodePrompt.Outcome.DECLINED
                        : current.unlock(owner, required.challenge());
                if (outcome != OneTimeCodePrompt.Outcome.UNLOCKED) {
                    throw new KnowledgeSourceException(KnowledgeSourceException.Kind.ACCESS_DENIED,
                            "Einmalcode nicht eingegeben", required);
                }
                return read.run();
            }
        }
    }

    private interface Read {
        KnowledgeDocument run() throws KnowledgeSourceException;
    }
}
