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
        String body = "<p>Text</p><p><div style=\"display:block;\" class=\"conf-macro output-block\""
                + " data-macro-name=\"drawio-sketch\"><div style=\"display:none\">Übersicht A</div>"
                + "<div class=\"drawio-macro\" style=\"width:300px;height:200px;\"></div>"
                + "<div style=\"display:none\" data-diagramdata=\"" + diagram("Übersicht A", 42, 3) + "\"></div>"
                + "</div></p><p>Danach</p>";

        ConfluencePageView view = ConfluencePageView.of(body, BASE);

        assertEquals(1, view.images.size());
        assertEquals(BASE + "download/attachments/42/%C3%9Cbersicht%20A.png?version=3&api=v2", view.images.get(0).src);
        assertEquals("Übersicht A", view.images.get(0).title);
        assertTrue(view.html, view.html.indexOf("%C3%9Cbersicht%20A.png") > view.html.indexOf("Text")
                && view.html.indexOf("%C3%9Cbersicht%20A.png") < view.html.indexOf("Danach"));
        assertFalse("Diagrammdaten nicht in der Ansicht", view.html.contains("data-diagramdata"));
    }

    @Test
    public void anIncludedDiagramLoadsFromItsOwningPage() {
        String body = "<div data-macro-name=\"inc-drawio\"><div style=\"display:none\" data-diagramdata=\""
                + diagram("Fremd", 99, 1) + "\"></div></div>"
                + "<div data-macro-name=\"drawio\"><div style=\"display:none\" data-diagramdata=\""
                + diagram("Eigen", 42, 7) + "\"></div></div>";

        ConfluencePageView view = ConfluencePageView.of(body, BASE);

        assertEquals(2, view.images.size());
        assertEquals(BASE + "download/attachments/99/Fremd.png?version=1&api=v2", view.images.get(0).src);
        assertEquals(BASE + "download/attachments/42/Eigen.png?version=7&api=v2", view.images.get(1).src);
    }

    private static String diagram(String name, int owningPageId, int revision) {
        return java.util.Base64.getEncoder().encodeToString(("{\"diagramName\":\"" + name + "\",\"previewName\":\""
                + name + ".png\",\"owningPageId\":" + owningPageId + ",\"revision\":" + revision + "}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
