package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Darstellung der Schritte des Einrichtungsassistenten in der Größe des Erststart-Fensters: Überschrift, Hinweis und
 * Felder liegen in eigenen Zeilen (vorher teilten sich Überschrift, Hinweis und erstes Feld eine Zeile und die
 * Schrift überlagerte sich), und der Zauberer des Schritts erscheint nur, wenn er an seinem Platz ganz in den
 * freien Raum passt. Mit {@code -DsetupWizard.screenshots=<dir>} schreibt der Test je Schritt ein Bild.
 */
public class SetupWizardLayoutTest {

    private static final int STEPS = SetupWizardPanel.STEP_SPEECH + 1;

    private final ComicPalette palette = ComicPalette.defaultPalette();

    private SetupWizardRun openRun(Dimension size) {
        SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), new WizardFakes.Settings(),
                new WizardFakes.Setup(), palette, completed -> {
                });
        run.overlay().setSize(size);
        layoutTree(run.overlay());
        return run;
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    /** Die Zeilen eines Schritts: das obere Raster der Seite (Überschrift, Hinweis, Felder). */
    private static Container rowsOf(SetupWizardPanel wizard, int step) {
        Container page = wizard.mascotOf(step).getParent();
        return (Container) page.getComponent(0);
    }

    @Test
    public void headingNoteAndFieldsDoNotOverlapInAnyStep() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun(new Dimension(SetupWizardWindow.WIDTH, SetupWizardWindow.HEIGHT));
            SetupWizardPanel wizard = run.wizard();
            for (int step = 0; step < STEPS; step++) {
                wizard.showStep(step);
                layoutTree(run.overlay());
                Container rows = rowsOf(wizard, step);
                Component[] parts = rows.getComponents();
                for (int i = 0; i < parts.length; i++) {
                    for (int j = i + 1; j < parts.length; j++) {
                        if (!parts[i].isVisible() || !parts[j].isVisible()) {
                            continue;
                        }
                        Rectangle a = parts[i].getBounds();
                        Rectangle b = parts[j].getBounds();
                        assertFalse("Schritt " + step + ": " + describe(parts[i]) + " überlappt " + describe(parts[j]),
                                a.intersects(b));
                    }
                }
                assertTrue("Schritt " + step + ": Überschrift zuoberst", parts[0].getY() < parts[1].getY());
            }
            capture(run);
        });
    }

    @Test
    public void mascotStandsInItsSlotBelowTheFieldsOfTheStep() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun(new Dimension(SetupWizardWindow.WIDTH, SetupWizardWindow.HEIGHT));
            SetupWizardPanel wizard = run.wizard();
            assertTrue("auf der ersten Seite ist unten genug Platz",
                    wizard.mascotOf(SetupWizardPanel.STEP_KEEPASS).fits());
            for (int step = 0; step < STEPS; step++) {
                wizard.showStep(step);
                layoutTree(run.overlay());
                SetupWizardMascot mascot = wizard.mascotOf(step);
                if (!mascot.fits()) {
                    continue;
                }
                Rectangle figure = SwingUtilities.convertRectangle(mascot, mascot.figureBounds(), wizard);
                Container rows = rowsOf(wizard, step);
                Rectangle fields = SwingUtilities.convertRectangle(rows.getParent(), rows.getBounds(), wizard);
                assertFalse("Schritt " + step + ": der Zauberer steht unter den Feldern", figure.intersects(fields));
                Rectangle area = SwingUtilities.convertRectangle(mascot.getParent(), mascot.getBounds(), wizard);
                assertTrue("Schritt " + step + ": ganz in der Fläche, nicht abgeschnitten", area.contains(figure));
                int middle = figure.x + figure.width / 2 - area.x;
                switch (mascot.placement()) {
                    case LEFT:
                        assertTrue("Schritt " + step + ": links", middle < area.width / 3);
                        break;
                    case RIGHT:
                        assertTrue("Schritt " + step + ": rechts", middle > area.width * 2 / 3);
                        break;
                    default:
                        assertTrue("Schritt " + step + ": mittig", Math.abs(middle - area.width / 2) <= 1);
                }
            }
        });
    }

    @Test
    public void everyStepKeepsMerlinVisibleAtTheDefaultWindowSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun(new Dimension(SetupWizardWindow.WIDTH, SetupWizardWindow.HEIGHT));
            SetupWizardPanel wizard = run.wizard();
            wizard.welcomeBack();
            for (int step = 0; step < STEPS; step++) {
                wizard.showStep(step);
                layoutTree(run.overlay());
                SetupWizardMascot mascot = wizard.mascotOf(step);
                assertTrue("Schritt " + step + ": Merlin passt in den freien Bereich (Fläche " + mascot.getSize()
                        + ", Figur " + mascot.figureSize() + ")", mascot.fits());
                if (mascot.spokenText().isEmpty()) {
                    mascot.say("Ein Satz von Merlin, damit die Sprechblase Platz braucht.");
                    layoutTree(run.overlay());
                }
                assertTrue("Schritt " + step + ": Sprechblase ist sichtbar", mascot.bubble().isVisible());
                Rectangle bubble = mascot.bubble().getBounds();
                assertTrue("Schritt " + step + ": Sprechblase ganz in der Fläche",
                        new Rectangle(mascot.getSize()).contains(bubble));
            }
        });
    }

    @Test
    public void mascotIsHiddenInsteadOfCutWhenTheSpaceIsTooSmall() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardMascot mascot = new SetupWizardMascot(SetupWizardMascot.Art.load(), 100, SetupWizardMascot.Placement.CENTER, 10, 0,
                    palette);
            Dimension figure = mascot.figureSize();
            assertTrue(figure.width > 0);
            mascot.setSize(figure.width, figure.height + 10);
            assertTrue("passt genau", mascot.fits());
            mascot.setSize(figure.width, figure.height + 9);
            assertFalse("einen Pixel zu niedrig: nicht zeichnen", mascot.fits());
            mascot.setSize(figure.width - 1, figure.height + 50);
            assertFalse("einen Pixel zu schmal: nicht zeichnen", mascot.fits());
            BufferedImage canvas = new BufferedImage(figure.width - 1, figure.height + 50, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            mascot.paint(g);
            g.dispose();
            for (int x = 0; x < canvas.getWidth(); x++) {
                for (int y = 0; y < canvas.getHeight(); y++) {
                    assertEquals("nichts gezeichnet", 0, canvas.getRGB(x, y) >>> 24);
                }
            }
        });
    }

    @Test
    public void mascotKeepsItsTransparentBackground() {
        SetupWizardMascot.Art art = SetupWizardMascot.Art.load();
        assertNotNull("Bild im Klassenpfad", art);
        BufferedImage small = art.scaled(art.widthFor(120), 120);
        assertEquals("Ecke durchsichtig", 0, small.getRGB(0, 0) >>> 24);
        assertTrue("Figur deckend", (small.getRGB(small.getWidth() / 2, small.getHeight() / 2) >>> 24) > 200);
    }

    @Test
    public void everyStepHasItsOwnTransparentWizard() {
        for (int step = 0; step < STEPS; step++) {
            String resource = SetupWizardPanel.mascotResource(step);
            SetupWizardMascot.Art art = SetupWizardMascot.Art.load(resource);
            assertNotNull(resource, art);
            BufferedImage small = art.scaled(art.widthFor(120), 120);
            assertEquals(resource + ": Ecke durchsichtig", 0, small.getRGB(0, 0) >>> 24);
        }
    }

    @Test
    public void tooSmallWizardShowsNoMascot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun(new Dimension(SetupWizardWindow.WIDTH, 420));
            SetupWizardPanel wizard = run.wizard();
            for (int step = 0; step < STEPS; step++) {
                assertFalse("Schritt " + step, wizard.mascotOf(step).fits());
            }
        });
    }

    private static String describe(Component c) {
        String text = c instanceof javax.swing.JLabel ? ((javax.swing.JLabel) c).getText()
                : c instanceof javax.swing.text.JTextComponent ? ((javax.swing.text.JTextComponent) c).getText() : "";
        return c.getClass().getSimpleName() + (text == null || text.isEmpty() ? ""
                : " „" + text.substring(0, Math.min(30, text.length())) + "“");
    }

    /** Optional: je Schritt ein Bild (zum Ansehen, keine Prüfung). */
    private static void capture(SetupWizardRun run) {
        String dir = System.getProperty("setupWizard.screenshots");
        if (dir == null || dir.isEmpty()) {
            return;
        }
        SetupWizardPanel wizard = run.wizard();
        wizard.welcomeBack(); // mit Merlins Sprechblasen
        JComponent overlay = run.overlay();
        for (int step = 0; step < STEPS; step++) {
            wizard.showStep(step);
            layoutTree(overlay);
            BufferedImage image = new BufferedImage(overlay.getWidth(), overlay.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            overlay.paint(g);
            g.dispose();
            try {
                new File(dir).mkdirs();
                ImageIO.write(image, "png", new File(dir, "schritt-" + (step + 1) + ".png"));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
