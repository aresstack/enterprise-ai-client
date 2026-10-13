package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der Play-Knopf des Lesebereichs: liest den Seitentext über dieselbe Sprachausgabe wie der Chat vor, ohne Bilder und
 * Diagramme, stoppt auf Klick und verstummt bei Reiterwechsel, Schließen oder neuem Inhalt; nie zwei Läufe.
 */
public class ReaderReadAloudTest {

    private static final String PAGE = "# Betrieb\n\nErster Absatz.\n\n![Skizze der Server](bild.png)\n\n"
            + "```mermaid\ngraph TD; A-->B;\n```\n\n- Punkt eins\n\n| Dienst | Zustand |\n|---|---|\n| Index | aktiv |";

    @Test
    public void playReadsTheActivePageWithoutVisualsAndASecondClickStops() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            assertTrue(fixture.reader.readAloudButton().isVisible());
            assertTrue(fixture.reader.readAloudButton().isEnabled());

            fixture.reader.readAloudButton().doClick();
            assertEquals(1, fixture.control.spoken.size());
            String spoken = fixture.control.spoken.get(0);
            assertTrue(spoken, spoken.contains("Betrieb"));
            assertTrue(spoken, spoken.contains("Erster Absatz."));
            assertTrue(spoken, spoken.contains("Punkt eins"));
            assertTrue(spoken, spoken.contains("Index"));
            assertFalse(spoken, spoken.contains("Skizze"));
            assertFalse(spoken, spoken.contains("bild.png"));
            assertFalse(spoken, spoken.contains("graph"));
            assertTrue(fixture.reader.isReadingAloud());
            assertEquals(KnowledgeReaderPanel.READ_ALOUD_STOP_TOOLTIP + " (Stimme)",
                    fixture.reader.readAloudButton().getToolTipText());

            fixture.reader.readAloudButton().doClick();
            assertEquals("Stopp statt eines zweiten Laufs", 1, fixture.control.spoken.size());
            assertEquals(1, fixture.control.stops);
            assertFalse(fixture.reader.isReadingAloud());
        });
    }

    @Test
    public void switchingTabsStopsTheOldPage() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            fixture.open("wiki:page/2", "Andere Seite.");

            assertFalse(fixture.reader.isReadingAloud());
            assertEquals(1, fixture.control.stops);
            // Das späte Ende des abgelösten Laufs ändert nichts mehr am neuen Reiter.
            fixture.control.finishAll();
            fixture.reader.readAloudButton().doClick();
            assertEquals("Andere Seite.", fixture.control.spoken.get(1));
            assertTrue(fixture.reader.isReadingAloud());
        });
    }

    @Test
    public void closingOrReloadingThePageStopsReading() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            String key = fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            fixture.reader.showMarkdown(key, "Neue Fassung.");
            assertFalse("neuer Inhalt: alter Text verstummt", fixture.reader.isReadingAloud());

            fixture.reader.readAloudButton().doClick();
            assertEquals("Neue Fassung.", fixture.control.spoken.get(1));
            fixture.reader.close(key);
            assertFalse(fixture.reader.isReadingAloud());
            assertEquals(2, fixture.control.stops);
        });
    }

    @Test
    public void theEndOfTheRunResetsTheButton() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            fixture.control.finishAll();
            assertFalse(fixture.reader.isReadingAloud());
            assertEquals(KnowledgeReaderPanel.READ_ALOUD_PLAY_TOOLTIP + " (Stimme)",
                    fixture.reader.readAloudButton().getToolTipText());
        });
    }

    @Test
    public void readingViewSpeaksTheDocumentTextNeverTheHtml() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            ReaderLink link = new ReaderLink("conf", "conf:page/7", "Seite", "", "conf:page/7");
            fixture.reader.open(link, true);
            fixture.reader.showHtml(link.key(), "<h1>Seite</h1><p>Text.</p><img src=\"x.png\" alt=\"Grafik\"/>", "",
                    Collections.<ReaderImage>emptyList(), (image, done) -> done.accept(null));
            assertFalse("noch kein Seitentext", fixture.reader.readAloudButton().isEnabled());

            fixture.reader.setSpeechText(link.key(), "# Seite\n\nText.");
            fixture.reader.readAloudButton().doClick();
            assertEquals("# Seite\n\nText.", fixture.control.spoken.get(0));
        });
    }

    @Test
    public void withoutSpeechOutputAClickExplainsWhyInsteadOfFailingSilently() throws Exception {
        final Fixture fixture = new Fixture(false);
        final List<String> notices = new ArrayList<String>();
        SwingUtilities.invokeAndWait(() -> {
            fixture.reader.setReadAloudNotice(notices::add);
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            assertTrue(fixture.control.spoken.isEmpty());
            assertEquals(1, notices.size());
            assertTrue(notices.get(0), notices.get(0).startsWith(KnowledgeReaderPanel.READ_ALOUD_UNAVAILABLE_TEXT));
            assertTrue(notices.get(0), notices.get(0).contains("Kein TTS-Modell"));
        });
    }

    @Test
    public void withoutControlTheButtonStaysHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
            }, (code, width) -> null);
            assertFalse(reader.readAloudButton().isVisible());
        });
    }

    private static final class Fixture {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final FakeControl control;

        Fixture(boolean available) {
            control = new FakeControl(available);
            reader.setReadAloud(control);
        }

        /** Öffnet die Seite angeheftet und zeigt den Text; liefert den Reiter-Schlüssel. */
        String open(String resourceId, String markdown) {
            ReaderLink link = new ReaderLink("wiki", resourceId, resourceId, "", resourceId);
            reader.open(link, true);
            reader.showMarkdown(link.key(), markdown);
            return link.key();
        }
    }

    /** Die Sprachausgabe als Attrappe: merkt sich den Text; das Ende meldet {@link #finishAll} wie die echte. */
    private static final class FakeControl implements ReadAloudControl {
        final boolean available;
        final List<String> spoken = new ArrayList<String>();
        final List<Runnable> pending = new ArrayList<Runnable>();
        int stops;

        FakeControl(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String description() {
            return available ? "Stimme" : "Kein TTS-Modell gewählt";
        }

        @Override
        public boolean autoStart() {
            return false;
        }

        @Override
        public void speak(String markdown) {
            speak(markdown, null);
        }

        @Override
        public void speak(String markdown, Runnable finished) {
            spoken.add(markdown);
            if (finished != null) {
                pending.add(finished);
            }
        }

        @Override
        public void stop() {
            stops++;
        }

        void finishAll() {
            List<Runnable> copy = new ArrayList<Runnable>(pending);
            pending.clear();
            for (Runnable finished : copy) {
                finished.run();
            }
        }

        @Override
        public void addListener(Listener listener) {
        }
    }
}
