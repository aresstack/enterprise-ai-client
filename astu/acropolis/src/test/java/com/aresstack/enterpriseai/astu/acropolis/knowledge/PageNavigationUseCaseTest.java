package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Issue #97: the use case behind the compass. */
public class PageNavigationUseCaseTest {

    private final PageNavigationUseCase navigation = new PageNavigationUseCase();
    private final InMemoryNavigationSource source = new InMemoryNavigationSource("wiki", 2)
            .space("DEV", "Development").page("DEV", "a", "A").page("a", "b", "B").page("a", "c", "C")
            .page("a", "d", "D").link("b", "c", "d").withIncoming();

    @Test
    public void positionNamesRootAndAncestors() throws Exception {
        PagePosition position = navigation.position(source, source.idOf("b"));

        assertEquals("space:DEV", position.root().key());
        assertEquals(1, position.ancestors().size());
        assertEquals(source.idOf("a"), position.ancestors().get(0).resource());
        assertEquals(3, position.path().size());
    }

    @Test
    public void childBatchesAreCachedPerSourceNodeAndCursor() throws Exception {
        PagePosition position = navigation.position(source, source.idOf("b"));
        NavigationChildren first = navigation.children(source, position.ancestors().get(0), "");
        NavigationChildren again = navigation.children(source, position.ancestors().get(0), "");
        NavigationChildren second = navigation.children(source, position.ancestors().get(0), first.nextCursor());

        assertSame(first, again);
        assertEquals(2, first.nodes().size());
        assertTrue(second.complete());
        assertEquals(1, second.nodes().size());
        int calls = 0;
        for (String call : source.calls()) {
            calls += call.startsWith("children ") ? 1 : 0;
        }
        assertEquals(2, calls);

        navigation.forget(source);
        navigation.children(source, position.ancestors().get(0), "");
        assertEquals(calls + 1, countChildren());
    }

    @Test
    public void crossReferencesComeFreshEveryTime() throws Exception {
        CrossReferences references = navigation.crossReferences(source, source.idOf("b"));
        navigation.crossReferences(source, source.idOf("b"));

        assertEquals(2, references.outgoing().size());
        assertTrue(references.incomingSupported());
        assertEquals(1, navigation.crossReferences(source, source.idOf("c")).incoming().size());
        assertEquals(3, source.calls().stream().filter(call -> call.startsWith("crossReferences")).count());
    }

    @Test
    public void aSourceWithoutHierarchyIsNotNavigable() throws Exception {
        InMemoryKnowledgeSource plain = new InMemoryKnowledgeSource("plain").add("x", "X", "text");

        assertFalse(navigation.supports(plain));
        assertNull(navigation.position(plain, plain.idOf("x")));
        assertNull(navigation.crossReferences(plain, plain.idOf("x")));
        assertTrue(navigation.children(plain, source.position(source.idOf("a")).root(), "").nodes().isEmpty());
    }

    @Test
    public void failuresOtherThanUnsupportedReachTheCaller() throws Exception {
        source.failWith("b", KnowledgeSourceException.Kind.ACCESS_DENIED);
        try {
            navigation.position(source, source.idOf("b"));
            fail("failure swallowed");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, e.kind());
        }
    }

    private int countChildren() {
        int calls = 0;
        for (String call : source.calls()) {
            calls += call.startsWith("children ") ? 1 : 0;
        }
        return calls;
    }
}
