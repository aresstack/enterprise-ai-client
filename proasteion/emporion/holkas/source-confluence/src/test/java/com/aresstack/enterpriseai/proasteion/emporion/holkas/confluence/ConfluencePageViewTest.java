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
                + "<script>alert(1)</script>"
                + "<div class=\"hidden\">Lade Vorgänge</div><p style=\"display: none\">Platzhalter</p>";

        ConfluencePageView view = ConfluencePageView.of(body, BASE);

        assertTrue(view.html, view.html.contains("Vorher <img"));
        assertTrue(view.html, view.html.contains("src=\"https://confluence.invalid/confluence/download/attachments/7/a.png"));
        assertTrue(view.html, view.html.contains("smile.svg")); // Emoticons bleiben im Text, nur nicht in der Liste
        assertFalse(view.html, view.html.contains("<script"));
        assertFalse(view.html, view.html.contains("Lade Vorgänge"));
        assertFalse(view.html, view.html.contains("Platzhalter"));
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

    @Test
    public void aDrawioDiagramShowsItsPreviewAttachment() {
        String data = java.util.Base64.getEncoder().encodeToString(("{\"diagramName\":\"Komponente Ü\","
                + "\"previewName\":\"Komponente Ü.png\",\"owningPageId\":17025995,\"revision\":55}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = "<p>Text</p><p><div style=\"display:block;\" class=\"conf-macro output-block\""
                + " data-macro-name=\"drawio-sketch\"><div style=\"display:none\">Komponente Ü</div>"
                + "<div class=\"drawio-macro\" style=\"width:1651px;height:1079px;\"></div>"
                + "<div style=\"display:none\" data-diagramdata=\"" + data + "\"></div></div></p>";

        ConfluencePageView view = ConfluencePageView.of(body, BASE);

        assertEquals(1, view.images.size());
        assertEquals(BASE + "download/attachments/17025995/Komponente%20%C3%9C.png", view.images.get(0).src);
        assertEquals("Komponente Ü", view.images.get(0).title);
        assertTrue(view.html, view.html.contains("Komponente%20%C3%9C.png"));
        assertFalse("Diagrammdaten nicht in der Ansicht", view.html.contains("data-diagramdata"));
    }

    @Test
    public void aDrawioMacroThatAlreadyHasAnImageGetsNoSecondOne() {
        String data = java.util.Base64.getEncoder().encodeToString(
                "{\"previewName\":\"D.png\",\"owningPageId\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = "<div data-macro-name=\"drawio\"><img src=\"/confluence/download/attachments/7/D.png\">"
                + "<div style=\"display:none\" data-diagramdata=\"" + data + "\"></div></div>";

        assertEquals(1, ConfluencePageView.of(body, BASE).images.size());
    }

    @Test
    public void spacesAndUmlautsInImageAddressesAreEncoded() {
        ConfluencePageView view = ConfluencePageView.of(
                "<img src=\"/confluence/download/attachments/7/Bild mit Ü.png?version=1\">", BASE);

        assertEquals(BASE + "download/attachments/7/Bild%20mit%20%C3%9C.png?version=1", view.images.get(0).src);
    }
}
