package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Die Lese-Ansicht behält die Bilder an ihrer Stelle und listet sie in Dokumentreihenfolge. */
public class ConfluencePageViewTest {

    private static final String BASE = "https://confluence.invalid/confluence/";

    @Test
    public void imagesStayInlineAndAreListedInDocumentOrder() {
        String body = "<h2>Überblick</h2>"
                + "<p>Vorher <img class=\"confluence-embedded-image\" src=\"/confluence/download/attachments/7/a.png"
                + "?version=2&amp;api=v2\" alt=\"Architektur\" data-linked-resource-id=\"101\""
                + " data-linked-resource-version=\"2\" data-linked-resource-type=\"attachment\""
                + " data-linked-resource-content-type=\"image/png\" data-linked-resource-container-id=\"7\"> nachher</p>"
                + "<p><img src=\"/confluence/images/icons/emoticons/smile.svg\" alt=\"(smile)\"></p>"
                + "<p><img src=\"https://other.invalid/logo.gif\"></p>"
                + "<script>alert(1)</script>";

        ConfluencePageView view = ConfluencePageView.of(body, BASE);

        assertTrue(view.html, view.html.contains("Vorher <img"));
        assertTrue(view.html, view.html.contains("src=\"https://confluence.invalid/confluence/download/attachments/7/a.png"));
        assertTrue(view.html, view.html.contains("smile.svg")); // Emoticons bleiben im Text, nur nicht in der Liste
        assertFalse(view.html, view.html.contains("<script"));
        assertTrue(view.html, view.html.contains("id=\"überblick\""));

        assertEquals(2, view.images.size());
        ConfluencePageView.InlineImage first = view.images.get(0);
        assertEquals("https://confluence.invalid/confluence/download/attachments/7/a.png?version=2&api=v2", first.src);
        assertEquals("att101", first.attachmentId);
        assertEquals("2", first.version);
        assertEquals("image/png", first.mediaType);
        assertEquals("Architektur", first.title);
        assertEquals("https://other.invalid/logo.gif", view.images.get(1).src);
        assertNull(view.images.get(1).attachmentId);
    }
}
