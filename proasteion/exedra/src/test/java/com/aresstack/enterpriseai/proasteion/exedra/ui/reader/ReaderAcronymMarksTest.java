package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymSettings;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Issue #107: acronym marks on the original HTML of a page, next to the find marks; long forms are invented. */
public class ReaderAcronymMarksTest {

    private static final String HTML = "<h2>IAR im Überblick</h2>"
            + "<p>Das Inventar aller Regenschirme (IAR) liefert an NIZZA. Die IAR-Schnittstelle bleibt.</p>"
            + "<p>Ein Link <a href=\"https://wiki.invalid/IAR\">IAR</a> und Code <code>IAR</code>.</p>"
            + "<pre>IAR in einem Block</pre>"
            + "<table><tr><td>NIZZA</td><td>Zelle</td></tr></table>"
            + "<p>DIESE ZEILE SCHREIT IAR LAUT</p>";

    private static ReaderAcronym iar() {
        return new ReaderAcronym(new LinkedHashSet<String>(Collections.singletonList("IAR")),
                Collections.singletonList("Inventar aller Regenschirme"));
    }

    private static ReaderAcronym nizza() {
        return new ReaderAcronym(new LinkedHashSet<String>(Collections.singletonList("NIZZA")),
                Collections.<String>emptyList());
    }

    private static KnowledgeReaderPanel readerWithPage(ReaderLink page) {
        KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
        reader.open(page, true);
        reader.showHtml(page.key(), HTML, "https://wiki.invalid/", Collections.<ReaderImage>emptyList(),
                (image, done) -> done.accept(null));
        return reader;
    }

    @Test
    public void marksProseButNotLinksCodePreformattedOrShouting() throws Exception {
        onEdt(() -> {
            ReaderLink page = new ReaderLink("wiki", "confluence:wiki.invalid/page/1", "Seite", "", "");
            KnowledgeReaderPanel reader = readerWithPage(page);
            reader.showAcronyms(page.key(), Arrays.asList(iar(), nizza()), AcronymSettings.defaults());
            assertEquals(Arrays.asList("IAR", "IAR", "NIZZA", "IAR", "NIZZA"), reader.acronymMarks(page.key()));
            assertTrue(reader.acronymsButton().isVisible());
        });
    }

    @Test
    public void tooltipOnlyWhereThePageStatesALongForm() throws Exception {
        onEdt(() -> {
            ReaderLink page = new ReaderLink("wiki", "confluence:wiki.invalid/page/2", "Seite", "", "");
            KnowledgeReaderPanel reader = readerWithPage(page);
            reader.showAcronyms(page.key(), Arrays.asList(iar(), nizza()), AcronymSettings.defaults());
            ReaderHtmlView view = reader.htmlView(page.key());
            String text = documentText(view);
            assertEquals(ReaderAcronym.TIP_ONE + "Inventar aller Regenschirme",
                    reader.acronymTooltip(page.key(), text.indexOf("IAR") + 1));
            assertNull(reader.acronymTooltip(page.key(), text.indexOf("NIZZA")));
            assertNull(reader.acronymTooltip(page.key(), text.indexOf("Überblick")));
        });
    }

    @Test
    public void conflictingLongFormsAreAllNamed() {
        ReaderAcronym two = new ReaderAcronym(new LinkedHashSet<String>(Collections.singletonList("IAR")),
                Arrays.asList("Inventar aller Regenschirme", "Initiative autonomer Roboter"));
        assertEquals(ReaderAcronym.TIP_MANY + "Inventar aller Regenschirme; Initiative autonomer Roboter",
                two.tooltip());
    }

    @Test
    public void findMarksAndAcronymMarksCoexist() throws Exception {
        onEdt(() -> {
            ReaderLink page = new ReaderLink("wiki", "confluence:wiki.invalid/page/3", "Seite", "", "");
            KnowledgeReaderPanel reader = readerWithPage(page);
            reader.showAcronyms(page.key(), Collections.singletonList(iar()), AcronymSettings.defaults());
            ReaderHtmlView view = reader.htmlView(page.key());
            int acronyms = view.getHighlighter().getHighlights().length;
            assertEquals(3, acronyms);
            assertEquals(1, reader.searchFor("Schnittstelle"));
            assertEquals(acronyms + 1, view.getHighlighter().getHighlights().length);
            assertEquals(0, reader.searchFor(""));
            assertEquals("the find bar leaves the acronym marks alone", acronyms,
                    view.getHighlighter().getHighlights().length);
        });
    }

    @Test
    public void theSwitchHidesAndShowsTheMarksOfEveryTab() throws Exception {
        onEdt(() -> {
            ReaderLink page = new ReaderLink("wiki", "confluence:wiki.invalid/page/4", "Seite", "", "");
            KnowledgeReaderPanel reader = readerWithPage(page);
            reader.showAcronyms(page.key(), Collections.singletonList(iar()), AcronymSettings.defaults());
            ReaderHtmlView view = reader.htmlView(page.key());
            reader.acronymsButton().doClick();
            assertFalse(reader.areAcronymMarksShown());
            assertEquals(0, view.getHighlighter().getHighlights().length);
            assertTrue(reader.acronymMarks(page.key()).isEmpty());
            assertEquals(KnowledgeReaderPanel.ACRONYMS_SHOW_TOOLTIP, reader.acronymsButton().getToolTipText());
            reader.acronymsButton().doClick();
            assertEquals(3, view.getHighlighter().getHighlights().length);
        });
    }

    @Test
    public void newContentDropsTheMarksAndOtherViewsIgnoreThem() throws Exception {
        onEdt(() -> {
            ReaderLink page = new ReaderLink("wiki", "confluence:wiki.invalid/page/5", "Seite", "", "");
            KnowledgeReaderPanel reader = readerWithPage(page);
            reader.showAcronyms(page.key(), Collections.singletonList(iar()), AcronymSettings.defaults());
            reader.showMarkdown(page.key(), "Die IAR als Text.");
            assertTrue(reader.acronymMarks(page.key()).isEmpty());
            assertFalse(reader.acronymsButton().isVisible());
            reader.showAcronyms(page.key(), Collections.singletonList(iar()), AcronymSettings.defaults());
            assertTrue("only the original HTML is marked", reader.acronymMarks(page.key()).isEmpty());
            reader.showAcronyms("wiki|unknown", Collections.singletonList(iar()), AcronymSettings.defaults());
        });
    }

    private static String documentText(ReaderHtmlView view) {
        try {
            return view.getDocument().getText(0, view.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private static void onEdt(Runnable body) throws InterruptedException, InvocationTargetException {
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

}
