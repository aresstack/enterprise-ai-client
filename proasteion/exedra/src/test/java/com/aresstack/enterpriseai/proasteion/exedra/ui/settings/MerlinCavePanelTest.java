package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Merlins Kristallhöhle: das Originalbild liegt auf dem Klassenpfad und ersetzt den Platzhalter. */
public class MerlinCavePanelTest {

    @Test
    public void caveImageIsOnTheClasspath() throws Exception {
        try (InputStream stream = MerlinCavePanel.class.getResourceAsStream("/merlin-crystal-cave.jpg")) {
            assertNotNull("merlin-crystal-cave.jpg fehlt auf dem Klassenpfad", stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        }
    }

    @Test
    public void panelShowsImageInsteadOfPlaceholderAndKeepsStatusAndButton() {
        MerlinCavePanel panel = new MerlinCavePanel(ComicPalette.defaultPalette());
        assertFalse(containsLabel(panel, "Merlins Kristallhöhle wartet auf ihr Bild."));
        assertTrue(containsLabel(panel, "Aufenthaltsort: Kristallhöhle  ·  Status: Standby seit Einführung der KI"));
        assertTrue(containsButton(panel, "Merlin um Rat fragen"));
    }

    private static boolean containsLabel(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) {
                return true;
            }
            if (child instanceof Container && containsLabel((Container) child, text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsButton(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) {
                return true;
            }
            if (child instanceof Container && containsButton((Container) child, text)) {
                return true;
            }
        }
        return false;
    }
}
