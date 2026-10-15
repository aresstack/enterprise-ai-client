package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** #132: figures of the reading view land in the export Markdown where the page shows them. */
public class ExportImagePlacementTest {

    private static ExportImage image(String src, String title) {
        return ExportImage.loaded(src, title, new byte[]{1});
    }

    @Test
    public void aFigureFollowsTheBlockWhoseWordsPrecedeItInTheView() {
        String markdown = "# Architektur\n\nDer Überblick zeigt alle Teile.\n\n## Betrieb\n\nNachts läuft der Job.\n";
        String html = "<h1>Architektur</h1><p>Der Überblick zeigt alle Teile.</p>"
                + "<p><img src=\"https://wiki/download/a.png?version=2\" alt=\"Überblick\"></p>"
                + "<h2>Betrieb</h2><p>Nachts läuft der Job.</p><img src=\"https://wiki/b.svg\">";
        String placed = ExportImagePlacement.place(markdown, html, Arrays.asList(
                image("https://wiki/download/a.png?version=2", "Überblick"), image("https://wiki/b.svg", "")));
        assertEquals("# Architektur\n\nDer Überblick zeigt alle Teile.\n\n"
                + "![Überblick](<https://wiki/download/a.png?version=2>)\n\n## Betrieb\n\nNachts läuft der Job.\n\n"
                + "![](<https://wiki/b.svg>)\n", placed);
    }

    @Test
    public void aFigureBeforeAnyTextComesFirstAndUnknownOnesGoLast() {
        String placed = ExportImagePlacement.place("Text der Seite.", "<img src=\"x/logo.png\"><p>Text der Seite.</p>",
                Arrays.asList(image("x/logo.png", "Logo"), image("elsewhere/gone.png", "Weg")));
        assertTrue(placed, placed.startsWith("![Logo](<x/logo.png>)\n\nText der Seite."));
        assertTrue(placed, placed.endsWith("![Weg](<elsewhere/gone.png>)\n"));
    }

    @Test
    public void aFigureNeverSplitsATable() {
        String markdown = "Werte:\n\n| A | B |\n|---|---|\n| eins | zwei |\n\nEnde.";
        String html = "<p>Werte:</p><table><tr><td>eins</td><td>zwei <img src=\"t.png\"></td></tr></table><p>Ende.</p>";
        String placed = ExportImagePlacement.place(markdown, html, Collections.singletonList(image("t.png", "")));
        assertTrue(placed, placed.contains("| eins | zwei |\n\n![](<t.png>)\n\nEnde."));
    }

    @Test
    public void aFigureShownTwiceIsPlacedTwice() {
        String placed = ExportImagePlacement.place("Erst hier.\n\nDann dort.",
                "<p>Erst hier.</p><img src=\"x/a.png\"><p>Dann dort.</p><img src=\"x/a.png\">",
                Collections.singletonList(image("x/a.png", "A")));
        assertEquals("Erst hier.\n\n![A](<x/a.png>)\n\nDann dort.\n\n![A](<x/a.png>)\n", placed);
    }

    @Test
    public void withoutFiguresTheTextStays() {
        assertEquals("Text", ExportImagePlacement.place("Text", "<p>Text</p>", Collections.<ExportImage>emptyList()));
    }
}
