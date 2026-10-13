package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/** Direkte Adresstreffer bleiben getrennt sichtbar und stehen vor den normalen Suchtreffern. */
public class SourceSearchStateTest {

    @Test
    public void directHitsPrecedeSearchHitsWithoutLosingEitherGroup() {
        ReaderLink direct = new ReaderLink("confluence://one", "confluence:one/page/1",
                "Direktadresse", "", "https://one/pages/viewpage.action?pageId=1");
        ReaderLink found = new ReaderLink("confluence://one", "confluence:one/page/2",
                "Gefunden", "", "confluence:one/page/2");

        SourceSearchState state = SourceSearchState.done("https://one/pages/viewpage.action?pageId=1", 1,
                Collections.singletonList(direct), Collections.singletonList(found), Collections.<String>emptyList());

        assertEquals(Collections.singletonList(direct), state.directHits());
        assertEquals(Collections.singletonList(found), state.searchHits());
        assertEquals(Arrays.asList(direct, found), state.hits());
        assertEquals(2, state.hits().size());
    }
}
