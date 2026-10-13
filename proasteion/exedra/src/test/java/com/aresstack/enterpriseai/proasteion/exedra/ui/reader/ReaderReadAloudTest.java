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
import static org.junit.Assert.assertNotNull;
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
    public void withAPausableOutputTheButtonPausesAndPlayContinuesWithoutStartingOver() throws Exception {
        final Fixture fixture = new Fixture(true, true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            assertEquals(KnowledgeReaderPanel.READ_ALOUD_PAUSE_TOOLTIP + " (Stimme)",
                    fixture.reader.readAloudButton().getToolTipText());

            for (int round = 1; round <= 2; round++) {
                fixture.reader.readAloudButton().doClick();
                assertEquals(round, fixture.control.pauses);
                assertTrue(fixture.reader.isReadAloudPaused());
                assertTrue("die Seite bleibt die vorgelesene", fixture.reader.isReadingAloud());
                assertEquals(KnowledgeReaderPanel.READ_ALOUD_RESUME_TOOLTIP + " (Stimme)",
                        fixture.reader.readAloudButton().getToolTipText());

                fixture.reader.readAloudButton().doClick();
                assertEquals(round, fixture.control.resumes);
                assertFalse(fixture.reader.isReadAloudPaused());
            }
            assertEquals("Fortsetzen startet keinen neuen Lauf vom Seitenanfang", 1, fixture.control.spoken.size());
            assertEquals("Pause ist kein Stopp", 0, fixture.control.stops);

            fixture.control.finishAll();
            assertFalse(fixture.reader.isReadingAloud());
            assertEquals(KnowledgeReaderPanel.READ_ALOUD_PLAY_TOOLTIP + " (Stimme)",
                    fixture.reader.readAloudButton().getToolTipText());
        });
    }

    @Test
    public void tabSwitchCloseOrNewContentDiscardAPausedPosition() throws Exception {
        final Fixture fixture = new Fixture(true, true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.readAloudButton().doClick();
            fixture.reader.readAloudButton().doClick();
            assertTrue(fixture.reader.isReadAloudPaused());
            fixture.open("wiki:page/2", "Andere Seite.");
            assertFalse(fixture.reader.isReadingAloud());
            assertFalse(fixture.reader.isReadAloudPaused());
            assertEquals(1, fixture.control.stops);

            fixture.reader.readAloudButton().doClick();
            assertEquals("Play nach dem Verwerfen beginnt die neue Seite", "Andere Seite.",
                    fixture.control.spoken.get(1));
            fixture.reader.readAloudButton().doClick();
            assertTrue(fixture.reader.isReadAloudPaused());
            fixture.reader.showMarkdown(new ReaderLink("wiki", "wiki:page/2", "wiki:page/2", "", "wiki:page/2").key(),
                    "Neue Fassung.");
            assertFalse("neuer Inhalt verwirft die Pause", fixture.reader.isReadAloudPaused());
            assertFalse(fixture.reader.isReadingAloud());
            assertEquals(2, fixture.control.stops);
        });
    }

    @Test
    public void aClickOnTheRateOpensPresetsThatMoveTheSliderOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ReadAloudRateControl rate = new ReadAloudRateControl(ReaderToolbar.buttonSize());
            List<Double> reported = new ArrayList<Double>();
            rate.onChange(reported::add);
            List<javax.swing.JPopupMenu> shown = new ArrayList<javax.swing.JPopupMenu>();
            rate.setMenuShower(shown::add);
            assertEquals(ReadAloudRateControl.PRESET_TOOLTIP, rate.valueLabel().getToolTipText());
            int width = rate.valueLabel().getPreferredSize().width;

            rate.setRate(0.7);
            assertTrue("setRate meldet nichts", reported.isEmpty());
            rate.valueLabel().dispatchEvent(new java.awt.event.MouseEvent(rate.valueLabel(),
                    java.awt.event.MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false));
            assertEquals(1, shown.size());
            javax.swing.JPopupMenu menu = shown.get(0);
            List<String> labels = new ArrayList<String>();
            for (java.awt.Component item : menu.getComponents()) {
                javax.swing.JRadioButtonMenuItem radio = (javax.swing.JRadioButtonMenuItem) item;
                labels.add(radio.getText());
                assertFalse("0,7× ist kein Preset: nichts markiert", radio.isSelected());
            }
            assertEquals(java.util.Arrays.asList("0,5\u00d7", "0,75\u00d7", "1,0\u00d7 \u2013 Normal", "1,25\u00d7",
                    "1,5\u00d7", "2,0\u00d7"), labels);

            ((javax.swing.JRadioButtonMenuItem) menu.getComponent(2)).doClick();
            assertEquals("genau eine Meldung", java.util.Collections.singletonList(1.0), reported);
            assertEquals(100, rate.slider().getValue());
            assertEquals("1,0\u00d7", rate.valueText());
            assertEquals("Breite bleibt stabil", width, rate.valueLabel().getPreferredSize().width);

            rate.openPresets();
            assertTrue("Normal ist jetzt markiert",
                    ((javax.swing.JRadioButtonMenuItem) shown.get(1).getComponent(2)).isSelected());
            ((javax.swing.JRadioButtonMenuItem) shown.get(1).getComponent(5)).doClick();
            assertEquals(java.util.Arrays.asList(1.0, 2.0), reported);
            assertEquals("2,0\u00d7", rate.valueText());

            // Enter und Leertaste öffnen das Menü ebenfalls.
            assertNotNull(rate.valueLabel().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)));
            assertNotNull(rate.valueLabel().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0)));
            assertTrue(rate.valueLabel().isFocusable());
        });
    }

    @Test
    public void rateSliderSitsBesideTheButtonAndSetsTheSpeakingRate() throws Exception {
        final Fixture fixture = new Fixture(true, true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            ReadAloudRateControl rate = fixture.reader.readAloudRate();
            assertTrue(rate.isVisible());
            assertEquals("1,0\u00d7", rate.valueText());
            assertEquals(java.util.Collections.<Double>emptyList(), fixture.control.rates);

            rate.slider().setValue(125);
            assertEquals(Double.valueOf(1.25), fixture.control.rates.get(0));
            assertEquals("1,25\u00d7", rate.valueText());
            fixture.reader.readAloudButton().doClick();
            fixture.reader.readAloudButton().doClick();
            rate.slider().setValue(80);
            assertEquals("auch während der Pause", Double.valueOf(0.8), fixture.control.rates.get(1));
            assertEquals("0,8\u00d7", rate.valueText());
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

    @Test
    public void doubleClickOnReadableTextRestartsFromThereAndElsewhereChangesNothing() throws Exception {
        final Fixture fixture = new Fixture(true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.open("wiki:page/1", PAGE);
            fixture.reader.setSize(900, 700);
            layout(fixture.reader);
            layout(fixture.reader);
            javax.swing.text.JTextComponent text = textContaining(fixture.reader, "Erster Absatz.");

            assertFalse("ohne laufendes Vorlesen passiert nichts",
                    fixture.reader.readAloudFrom(text, pointOf(text, "Absatz")));
            assertTrue(fixture.control.spoken.isEmpty());

            fixture.reader.readAloudButton().doClick();
            assertTrue(fixture.reader.readAloudFrom(text, pointOf(text, "Absatz")));
            assertEquals(2, fixture.control.spoken.size());
            String restarted = fixture.control.spoken.get(1);
            assertTrue(restarted, restarted.startsWith("Absatz."));
            assertTrue(restarted, restarted.contains("Punkt eins"));
            assertTrue(fixture.reader.isReadingAloud());

            // Das Ende des abgelösten Laufs setzt den Knopf nicht zurück.
            fixture.control.pending.remove(0).run();
            assertTrue(fixture.reader.isReadingAloud());

            int stops = fixture.control.stops;
            assertFalse("Leerraum unter dem Text", fixture.reader.readAloudFrom(text,
                    new java.awt.Point(text.getWidth() - 2, text.getHeight() - 2)));
            assertFalse("außerhalb der Seite", fixture.reader.readAloudFrom(fixture.reader.readAloudButton(),
                    new java.awt.Point(1, 1)));
            assertEquals(2, fixture.control.spoken.size());
            assertEquals(stops, fixture.control.stops);
            assertTrue(fixture.reader.isReadingAloud());
        });
    }

    private static void layout(java.awt.Component component) {
        if (component instanceof java.awt.Container) {
            ((java.awt.Container) component).doLayout();
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static javax.swing.text.JTextComponent textContaining(java.awt.Component component, String needle) {
        if (component instanceof javax.swing.text.JTextComponent
                && ((javax.swing.text.JTextComponent) component).getText().contains(needle)) {
            return (javax.swing.text.JTextComponent) component;
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                javax.swing.text.JTextComponent found = textContaining(child, needle);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static java.awt.Point pointOf(javax.swing.text.JTextComponent text, String word) {
        try {
            String content = text.getDocument().getText(0, text.getDocument().getLength());
            java.awt.Rectangle at = text.modelToView(content.indexOf(word) + 1);
            return new java.awt.Point(at.x, at.y + at.height / 2);
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Fixture {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final FakeControl control;

        Fixture(boolean available) {
            this(available, false);
        }

        Fixture(boolean available, boolean pausable) {
            control = new FakeControl(available, pausable);
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
        final boolean pausable;
        final List<String> spoken = new ArrayList<String>();
        final List<Runnable> pending = new ArrayList<Runnable>();
        final List<Double> rates = new ArrayList<Double>();
        int stops;
        int pauses;
        int resumes;
        double rate = 1.0;

        FakeControl(boolean available, boolean pausable) {
            this.available = available;
            this.pausable = pausable;
        }

        @Override
        public boolean canPause() {
            return pausable;
        }

        @Override
        public void pause() {
            pauses++;
        }

        @Override
        public void resume() {
            resumes++;
        }

        @Override
        public void setRate(double rate) {
            this.rate = rate;
            rates.add(rate);
        }

        @Override
        public double rate() {
            return rate;
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
