package com.aresstack.enterpriseai.source.mediawiki;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiResourceIdsTest {

    @Test
    public void idIsStableReadableAndAValidUri() {
        String id = WikiResourceIds.idFor("intranet", "Hilfe:Größe & Maß/Unterseite");
        assertEquals("wiki:intranet/Hilfe:Gr%C3%B6%C3%9Fe_%26_Ma%C3%9F/Unterseite", id);
        URI uri = URI.create(id);
        assertEquals("wiki", uri.getScheme());
    }

    @Test
    public void spacesAndUnderscoresMapToTheSameId() {
        assertEquals(WikiResourceIds.idFor("w", "Main Page"), WikiResourceIds.idFor("w", "Main_Page"));
        assertEquals(WikiResourceIds.idFor("w", " Main Page "), WikiResourceIds.idFor("w", "Main_Page"));
    }

    @Test
    public void roundTripsTitles() {
        String[] titles = {"Main Page", "C++ (Sprache)", "Ä/ö/ü", "100%", "Plus+Minus", "a?b#c"};
        for (String title : titles) {
            assertEquals(title, WikiResourceIds.titleOf("w", WikiResourceIds.idFor("w", title)));
        }
    }

    @Test
    public void rejectsForeignOrMalformedIds() {
        assertNull(WikiResourceIds.titleOf("w", "wiki:other/Page"));
        assertNull(WikiResourceIds.titleOf("w", "confluence:w/Page"));
        assertNull(WikiResourceIds.titleOf("w", "wiki:w/"));
        assertNull(WikiResourceIds.titleOf("w", "wiki:w/%ZZ"));
        assertNull(WikiResourceIds.titleOf("w", "wiki:w/%4"));
        assertNull(WikiResourceIds.titleOf("w", null));
    }
}
