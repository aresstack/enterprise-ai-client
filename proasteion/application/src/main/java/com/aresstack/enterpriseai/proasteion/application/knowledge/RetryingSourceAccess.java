package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SourceCalls;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Wiederholt lesende Quellenzugriffe (Live-Suche und Seitenabruf), wenn die Quelle vorübergehend nicht erreichbar ist
 * ({@link KnowledgeSourceException.Kind#UNAVAILABLE}: Netz, Timeout, 5xx). Eine fehlende Seite, ein verweigerter
 * Zugriff, eine fehlerhafte Antwort, eine fremde ID und eine Einmalcode-Abfrage gehen sofort an den Aufrufer, der
 * wie bisher mit den übrigen Treffern weitermacht. Die Regel ({@code knowledge.sources.retry*}) gilt je Aufruf, also
 * ohne Neustart.
 */
public final class RetryingSourceAccess implements SourceCalls {

    private static final Logger LOG = Logger.getLogger(RetryingSourceAccess.class.getName());

    private final Supplier<RetryPolicy> policy;
    private final RetryPolicy.Sleeper sleeper;

    public RetryingSourceAccess(Supplier<RetryPolicy> policy) {
        this(policy, RetryPolicy.Sleeper.threadSleep());
    }

    public RetryingSourceAccess(Supplier<RetryPolicy> policy, RetryPolicy.Sleeper sleeper) {
        if (policy == null || sleeper == null) {
            throw new IllegalArgumentException("policy and sleeper must not be null");
        }
        this.policy = policy;
        this.sleeper = sleeper;
    }

    /** Nur „Quelle nicht erreichbar“ ist vorübergehend (eine Einmalcode-Abfrage ist ACCESS_DENIED). */
    public static boolean isTransient(KnowledgeSourceException failure) {
        return failure.kind() == KnowledgeSourceException.Kind.UNAVAILABLE;
    }

    @Override
    public <T> T call(final Call<T> call) throws KnowledgeSourceException {
        RetryPolicy current = policy.get();
        if (current == null) {
            current = RetryPolicy.none();
        }
        return current.call(call::run, KnowledgeSourceException.class, RetryingSourceAccess::isTransient,
                (retry, retries, delay, failure) -> LOG.info("Quelle vorübergehend nicht erreichbar ("
                        + failure.getMessage() + "); Wiederholung " + retry + " von " + retries + " in " + delay
                        + " ms"),
                sleeper);
    }

    /** {@code reader} mit derselben Wiederholungsregel für Lesen und Neuholen einer Seite. */
    public KnowledgeDocumentReader reader(final KnowledgeDocumentReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        return new KnowledgeDocumentReader() {
            @Override
            public KnowledgeDocument read(final SourceAddress owner, final KnowledgeSourcePort source,
                                          final KnowledgeResourceId id) throws KnowledgeSourceException {
                return call(() -> reader.read(owner, source, id));
            }

            @Override
            public KnowledgeDocument refresh(final SourceAddress owner, final KnowledgeSourcePort source,
                                             final KnowledgeResourceId id) throws KnowledgeSourceException {
                return call(() -> reader.refresh(owner, source, id));
            }

            @Override
            public String toString() {
                return "Retrying[" + reader + "]";
            }
        };
    }
}
