package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RetryingSourceAccessTest {

    private final List<Long> pauses = new ArrayList<Long>();
    private final RetryingSourceAccess access = new RetryingSourceAccess(() -> RetryPolicy.of(2, 500), pauses::add);
    private final LinkedList<KnowledgeSourceException> failures = new LinkedList<KnowledgeSourceException>();
    private int calls;

    @Test
    public void unavailableSourceIsRetried() throws KnowledgeSourceException {
        failures.add(new KnowledgeSourceException(Kind.UNAVAILABLE, "HTTP 503"));
        failures.add(new KnowledgeSourceException(Kind.UNAVAILABLE, "Timeout"));

        assertEquals("Treffer", access.call(this::search));
        assertEquals(3, calls);
        assertEquals(Arrays.asList(500L, 1250L), pauses);
    }

    @Test
    public void businessFailuresAreNotRetried() {
        for (Kind kind : Arrays.asList(Kind.NOT_FOUND, Kind.ACCESS_DENIED, Kind.INVALID_RESPONSE, Kind.UNSUPPORTED)) {
            assertFalse(kind.name(), RetryingSourceAccess.isTransient(new KnowledgeSourceException(kind, "x")));
            calls = 0;
            failures.add(new KnowledgeSourceException(kind, "fachlich"));
            try {
                access.call(this::search);
                fail();
            } catch (KnowledgeSourceException expected) {
                assertEquals(kind.name(), 1, calls);
            }
        }
        assertTrue(pauses.isEmpty());
    }

    private String search() throws KnowledgeSourceException {
        calls++;
        KnowledgeSourceException failure = failures.poll();
        if (failure != null) {
            throw failure;
        }
        return "Treffer";
    }
}
