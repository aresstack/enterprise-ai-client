package com.aresstack.enterpriseai.source.mediawiki;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WikiHtmlTextTest {

    @Test
    public void keepsHeadingsParagraphsListsAndTables() {
        String html = "<div class=\"mw-parser-output\">"
                + "<p>Einleitung mit <b>Fett</b> und <a href=\"/wiki/X\">Link</a>.</p>"
                + "<div id=\"toc\" class=\"toc\"><ul><li>1 Abschnitt</li></ul></div>"
                + "<h2><span class=\"mw-headline\" id=\"Abschnitt\">Abschnitt</span>"
                + "<span class=\"mw-editsection\">[bearbeiten]</span></h2>"
                + "<p>Erster\n  Absatz.</p><ul><li>Eins</li><li>Zwei</li></ul>"
                + "<h3>Tabelle</h3><table><tr><th>Name</th><th>Wert</th></tr><tr><td>a</td><td>1</td></tr></table>"
                + "<pre>code  bleibt\n  eingerückt</pre>"
                + "</div>";

        String text = WikiHtmlText.toText(html);

        assertEquals("Einleitung mit Fett und Link.\n\n"
                + "## Abschnitt\n\n"
                + "Erster Absatz.\n\n"
                + "- Eins\n- Zwei\n\n"
                + "### Tabelle\n\n"
                + "Name | Wert\na | 1\n\n"
                + "code  bleibt\n  eingerückt", text);
    }

    @Test
    public void removesScriptsStylesImagesReferencesAndHiddenElements() {
        String html = "<p>Text<sup class=\"reference\">[1]</sup></p><script>alert(1)</script>"
                + "<style>.x{}</style><div class=\"thumb\"><img src=\"a.png\"><div>Bildunterschrift</div></div>"
                + "<div class=\"noprint\">Navigation</div><span style=\"display: none\">versteckt</span>";

        String text = WikiHtmlText.toText(html);

        assertEquals("Text", text);
    }

    @Test
    public void handlesUmlautsEntitiesAndEmptyInput() {
        assertEquals("Größe & Maß – „Zitat“", WikiHtmlText.toText("<p>Größe &amp; Maß &ndash; &bdquo;Zitat&ldquo;</p>"));
        assertEquals("", WikiHtmlText.toText(null));
        assertEquals("", WikiHtmlText.toText(""));
    }

    @Test
    public void lineBreaksBecomeNewlines() {
        String text = WikiHtmlText.toText("<p>Zeile 1<br>Zeile 2</p>");
        assertEquals("Zeile 1\nZeile 2", text);
        assertFalse(text.contains("<"));
        assertTrue(WikiHtmlText.toText("<h1>T</h1>").startsWith("# T"));
    }
}
