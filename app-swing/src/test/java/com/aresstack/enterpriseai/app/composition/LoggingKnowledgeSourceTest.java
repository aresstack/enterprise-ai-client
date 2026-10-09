package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Die Hülle reicht Ergebnisse durch und protokolliert Fehlschläge mit Ursachenkette, ohne sie zu verändern. */
public class LoggingKnowledgeSourceTest {

    private final List<LogRecord> records = new ArrayList<LogRecord>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final Logger logger = Logger.getLogger(LoggingKnowledgeSource.class.getName());

    @Before
    public void attach() {
        logger.addHandler(capture);
    }

    @After
    public void detach() {
        logger.removeHandler(capture);
    }

    @Test
    public void failuresAreLoggedWithTheCauseChainAndRethrownUnchanged() throws Exception {
        final IOException cause = new IOException("PKIX path building failed");
        final KnowledgeSourceException failure = new KnowledgeSourceException(
                KnowledgeSourceException.Kind.UNAVAILABLE, "connection to wiki.example failed", cause);
        LoggingKnowledgeSource source = new LoggingKnowledgeSource(new FailingSource(failure));
        assertEquals("wiki", source.sourceId().value());

        try {
            source.discover(SourceScope.of("Kategorie:Handbuch"));
            fail("expected KnowledgeSourceException");
        } catch (KnowledgeSourceException e) {
            assertSame(failure, e);
        }
        assertEquals(1, records.size());
        LogRecord record = records.get(0);
        assertEquals(Level.WARNING, record.getLevel());
        assertTrue(record.getMessage(), record.getMessage().startsWith("Quelle wiki: discover "));
        assertTrue(record.getMessage(), record.getMessage().contains("UNAVAILABLE"));
        assertTrue(record.getMessage(), record.getMessage().contains("connection to wiki.example failed"));
        assertSame("die Ausnahme samt Ursache geht an den Handler", failure, record.getThrown());
        assertSame(cause, record.getThrown().getCause());

        try {
            source.load(KnowledgeResourceId.of("wiki", "Seite"));
            fail("expected KnowledgeSourceException");
        } catch (KnowledgeSourceException e) {
            assertSame(failure, e);
        }
        assertEquals(2, records.size());
        assertTrue(records.get(1).getMessage(), records.get(1).getMessage().startsWith("Quelle wiki: load "));
    }

    @Test
    public void successfulCallsPassThroughWithoutLogging() throws Exception {
        LoggingKnowledgeSource source = new LoggingKnowledgeSource(new FailingSource(null));
        assertTrue(source.discover(SourceScope.of("x")).isEmpty());
        assertTrue(source.discoverLinks(KnowledgeResourceId.of("wiki", "Seite")).isEmpty());
        assertTrue(records.isEmpty());
        assertTrue(source.toString(), source.toString().startsWith("Logging("));
    }

    /** Wirft bei jedem Zugriff die vorgegebene Ausnahme; ohne Ausnahme liefert sie leere Ergebnisse. */
    private static final class FailingSource implements KnowledgeSourcePort {
        private final KnowledgeSourceException failure;

        FailingSource(KnowledgeSourceException failure) {
            this.failure = failure;
        }

        @Override
        public KnowledgeSourceId sourceId() {
            return KnowledgeSourceId.of("wiki");
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            if (failure != null) {
                throw failure;
            }
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            if (failure != null) {
                throw failure;
            }
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "kein Dokument");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            if (failure != null) {
                throw failure;
            }
            return Collections.emptyList();
        }
    }
}
